package com.marketinghub.campaign;

import com.marketinghub.campaign.dto.CampaignDto;
import com.marketinghub.campaign.worker.AmqpConfig;
import com.marketinghub.campaign.worker.CampaignSendMessage;
import com.marketinghub.customer.Customer;
import com.marketinghub.customer.CustomerRepository;
import com.marketinghub.template.MessageTemplate;
import com.marketinghub.template.MessageTemplateRepository;
import com.marketinghub.template.TemplateNotFoundException;
import com.marketinghub.template.TemplateStatus;
import com.marketinghub.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 13 launch path: transitions the campaign to SENDING, gathers its PENDING
 * recipients + their customer phone numbers, then — only after the tx commits —
 * publishes one CampaignSendMessage per recipient onto the campaign.send queue.
 */
@Service
public class CampaignLaunchService {

    private static final Logger log = LoggerFactory.getLogger(CampaignLaunchService.class);

    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository recipientRepository;
    private final MessageTemplateRepository templateRepository;
    private final CustomerRepository customerRepository;
    private final CampaignService campaignService;
    private final RabbitTemplate rabbitTemplate;

    /**
     * When WhatsApp is mocked (local dev) we don't enforce the "template must be Meta-approved"
     * launch guard — there's no real Meta to approve anything, and sends are faked anyway.
     * In live mode the guard turns a silent per-recipient 132001 into one clear pre-launch error.
     */
    private final boolean whatsAppMock;

    public CampaignLaunchService(
        CampaignRepository campaignRepository,
        CampaignRecipientRepository recipientRepository,
        MessageTemplateRepository templateRepository,
        CustomerRepository customerRepository,
        CampaignService campaignService,
        RabbitTemplate rabbitTemplate,
        @Value("${whatsapp.mock:false}") boolean whatsAppMock
    ) {
        this.campaignRepository = campaignRepository;
        this.recipientRepository = recipientRepository;
        this.templateRepository = templateRepository;
        this.customerRepository = customerRepository;
        this.campaignService = campaignService;
        this.rabbitTemplate = rabbitTemplate;
        this.whatsAppMock = whatsAppMock;
    }

    @Transactional
    public CampaignDto launch(UUID campaignId) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("No tenant context");
        }
        Campaign campaign = campaignRepository.findByIdAndTenantId(campaignId, tenantId)
            .orElseThrow(() -> new CampaignNotFoundException(campaignId));
        if (campaign.getStatus() != CampaignStatus.DRAFT
            && campaign.getStatus() != CampaignStatus.SCHEDULED) {
            throw new InvalidCampaignStateException(
                "Only DRAFT or SCHEDULED campaigns can be launched (current: " + campaign.getStatus() + ")");
        }
        // Resolve what we'll actually send. TEMPLATE campaigns ride on an approved Meta template;
        // FREE_TEXT campaigns carry their own body and go out as plain text (24h-window only).
        MessageTemplate template = null;
        String rawBody;
        if (campaign.getSendMode() == CampaignSendMode.FREE_TEXT) {
            rawBody = campaign.getBodyText();
        } else {
            template = templateRepository.findByIdAndTenantId(campaign.getTemplateId(), tenantId)
                .orElseThrow(() -> new TemplateNotFoundException(campaign.getTemplateId()));
            // Live-mode guard: Meta rejects sends on non-approved templates (error 132001).
            // Surface that as one clear error at launch instead of N failed recipients.
            if (!whatsAppMock && template.getStatus() != TemplateStatus.APPROVED) {
                throw new InvalidCampaignStateException(
                    "Template '" + template.getName() + "' is not approved by Meta (status="
                        + template.getStatus() + "). Sync templates from Meta and get it approved "
                        + "before launching.");
            }
            rawBody = template.getBodyPreview() == null ? template.getName() : template.getBodyPreview();
        }

        // Snapshot the recipients + customer phone numbers BEFORE we hand off to the queue.
        List<CampaignRecipient> recipients = recipientRepository
            .findAllByCampaignIdOrderByCreatedAtAsc(campaign.getId())
            .stream()
            .filter(r -> r.getStatus() == CampaignRecipientStatus.PENDING)
            .toList();
        Set<UUID> customerIds = new HashSet<>();
        recipients.forEach(r -> customerIds.add(r.getCustomerId()));
        Map<UUID, Customer> byId = new HashMap<>();
        if (!customerIds.isEmpty()) {
            customerRepository.findAllByIdInAndTenantId(customerIds, tenantId)
                .forEach(c -> byId.put(c.getId(), c));
        }

        List<CampaignSendMessage> envelopes = new ArrayList<>(recipients.size());
        for (CampaignRecipient r : recipients) {
            Customer c = byId.get(r.getCustomerId());
            if (c == null) {
                // Customer was deleted between create and launch — mark FAILED here.
                r.setStatus(CampaignRecipientStatus.FAILED);
                r.setErrorMessage("Customer no longer exists");
                continue;
            }
            // Personalize per recipient: the rendered text is stored/shown in-app; the
            // template variables ({{1}} = customer name) are sent to Meta as body params.
            String body = renderBody(rawBody, c);
            // FREE_TEXT: templateName == null tells the worker to call sendText(body).
            // TEMPLATE: pass the Meta template name/language + the {{1}} body param.
            envelopes.add(new CampaignSendMessage(
                tenantId,
                campaign.getId(),
                r.getId(),
                c.getId(),
                template == null ? null : template.getId(),
                c.getPhoneE164(),
                body,
                template == null ? null : template.getWhatsappTemplateName(),
                template == null ? null : template.getLanguage(),
                template == null ? java.util.List.of() : templateBodyParams(rawBody, c)
            ));
        }

        campaign.setStatus(CampaignStatus.SENDING);
        campaign.setStartedAt(Instant.now());

        // Fan out only after the tx commits — the worker reads from a separate connection
        // and would not see the SENDING status / recipient rows otherwise.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                int n = 0;
                for (CampaignSendMessage m : envelopes) {
                    rabbitTemplate.convertAndSend(
                        AmqpConfig.CAMPAIGN_SEND_EXCHANGE,
                        AmqpConfig.CAMPAIGN_SEND_ROUTING_KEY,
                        m);
                    n++;
                }
                log.info("Campaign {} launched — published {} messages to {}",
                    campaign.getId(), n, AmqpConfig.CAMPAIGN_SEND_QUEUE);
            }
        });

        // If there were no eligible recipients, complete immediately so the UI doesn't
        // sit on SENDING forever.
        if (envelopes.isEmpty()) {
            campaign.setStatus(CampaignStatus.SENT);
            campaign.setCompletedAt(Instant.now());
        }

        return campaignService.get(campaign.getId());
    }

    /**
     * Fills personalization placeholders in a template body for one customer. We support
     * the WhatsApp-style positional token {{1}} and a friendlier {{name}}, both mapped to
     * the customer's name (falling back to "there" when we have no name on file). Any other
     * {{...}} tokens are stripped so customers never see raw placeholder syntax.
     */
    private static final java.util.regex.Pattern PLACEHOLDER =
        java.util.regex.Pattern.compile("\\{\\{\\s*\\d+\\s*\\}\\}");

    /**
     * Body parameters for the WhatsApp template, filling {{1}}, {{2}}, ... The app only holds
     * one personalization value (the customer's name → {{1}}), so we send a single param when
     * the template has any variable, none otherwise. Multi-variable templates would need a
     * per-campaign parameter model (future work) — they'll surface a clear Meta error until then.
     */
    static List<String> templateBodyParams(String rawBody, Customer c) {
        if (rawBody == null || !PLACEHOLDER.matcher(rawBody).find()) {
            return List.of();
        }
        String name = (c.getFullName() != null && !c.getFullName().isBlank())
            ? c.getFullName().trim()
            : "there";
        return List.of(name);
    }

    static String renderBody(String rawBody, Customer c) {
        if (rawBody == null) return "";
        String name = (c.getFullName() != null && !c.getFullName().isBlank())
            ? c.getFullName().trim()
            : "there";
        return rawBody
            .replace("{{1}}", name)
            .replace("{{name}}", name)
            // Remove any leftover {{...}} placeholders we don't have data for.
            .replaceAll("\\{\\{[^}]*\\}\\}", "")
            .trim();
    }
}
