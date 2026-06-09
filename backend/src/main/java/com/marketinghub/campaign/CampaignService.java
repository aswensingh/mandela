package com.marketinghub.campaign;

import com.marketinghub.auth.AuthenticatedPrincipal;
import com.marketinghub.campaign.dto.CampaignDto;
import com.marketinghub.campaign.dto.CreateCampaignRequest;
import com.marketinghub.campaign.dto.RecipientDto;
import com.marketinghub.campaign.dto.UpdateCampaignRequest;
import com.marketinghub.customer.Customer;
import com.marketinghub.customer.CustomerRepository;
import com.marketinghub.template.MessageTemplate;
import com.marketinghub.template.MessageTemplateRepository;
import com.marketinghub.template.TemplateNotFoundException;
import com.marketinghub.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository recipientRepository;
    private final MessageTemplateRepository templateRepository;
    private final CustomerRepository customerRepository;

    public CampaignService(
        CampaignRepository campaignRepository,
        CampaignRecipientRepository recipientRepository,
        MessageTemplateRepository templateRepository,
        CustomerRepository customerRepository
    ) {
        this.campaignRepository = campaignRepository;
        this.recipientRepository = recipientRepository;
        this.templateRepository = templateRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CampaignDto create(CreateCampaignRequest request) {
        UUID tenantId = requireTenant();
        UUID userId = currentUserId();

        // Default to TEMPLATE for back-compat when the client omits the mode.
        CampaignSendMode sendMode = request.sendMode() == null
            ? CampaignSendMode.TEMPLATE
            : request.sendMode();

        // Mode-specific payload validation (a record can't express conditional requireds).
        MessageTemplate template = null;
        if (sendMode == CampaignSendMode.TEMPLATE) {
            if (request.templateId() == null) {
                throw new InvalidCampaignStateException(
                    "templateId is required for a TEMPLATE campaign");
            }
            // Template must belong to this tenant.
            template = templateRepository
                .findByIdAndTenantId(request.templateId(), tenantId)
                .orElseThrow(() -> new TemplateNotFoundException(request.templateId()));
        } else {
            if (request.bodyText() == null || request.bodyText().isBlank()) {
                throw new InvalidCampaignStateException(
                    "bodyText is required for a FREE_TEXT campaign");
            }
        }

        // All customer IDs must belong to this tenant. Dedup first to avoid double counting.
        Set<UUID> uniqueCustomerIds = new HashSet<>(request.customerIds());
        long matched = customerRepository.countByIdInAndTenantId(uniqueCustomerIds, tenantId);
        if (matched != uniqueCustomerIds.size()) {
            throw new InvalidCampaignStateException(
                "Some customer IDs are not in this tenant or do not exist");
        }

        Campaign campaign = new Campaign();
        campaign.setTenantId(tenantId);
        campaign.setName(request.name());
        campaign.setStatus(CampaignStatus.DRAFT);
        campaign.setSendMode(sendMode);
        campaign.setTemplateId(template == null ? null : template.getId());
        campaign.setBodyText(sendMode == CampaignSendMode.FREE_TEXT ? request.bodyText() : null);
        campaign.setScheduledAt(request.scheduledAt());
        campaign.setCreatedByUserId(userId);
        Campaign savedCampaign = campaignRepository.save(campaign);
        campaignRepository.flush();

        for (UUID customerId : uniqueCustomerIds) {
            CampaignRecipient r = new CampaignRecipient();
            r.setCampaignId(savedCampaign.getId());
            r.setCustomerId(customerId);
            r.setStatus(CampaignRecipientStatus.PENDING);
            recipientRepository.save(r);
        }
        recipientRepository.flush();

        return buildDto(savedCampaign, template);
    }

    @Transactional(readOnly = true)
    public Page<CampaignDto> list(Pageable pageable) {
        UUID tenantId = requireTenant();
        Page<Campaign> page = campaignRepository.findAllByTenantId(tenantId, pageable);
        // Batch-load template names for every campaign in the page (FREE_TEXT campaigns have none).
        Set<UUID> templateIds = new HashSet<>();
        page.forEach(c -> {
            if (c.getTemplateId() != null) {
                templateIds.add(c.getTemplateId());
            }
        });
        Map<UUID, MessageTemplate> templatesById = new HashMap<>();
        if (!templateIds.isEmpty()) {
            templateRepository.findAllById(templateIds)
                .forEach(t -> templatesById.put(t.getId(), t));
        }
        return page.map(c -> buildDto(c, templatesById.get(c.getTemplateId())));
    }

    @Transactional(readOnly = true)
    public CampaignDto get(UUID id) {
        UUID tenantId = requireTenant();
        Campaign c = campaignRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new CampaignNotFoundException(id));
        return buildDto(c, lookupTemplate(c));
    }

    @Transactional(readOnly = true)
    public Page<RecipientDto> listRecipients(UUID campaignId, Pageable pageable) {
        UUID tenantId = requireTenant();
        Campaign c = campaignRepository.findByIdAndTenantId(campaignId, tenantId)
            .orElseThrow(() -> new CampaignNotFoundException(campaignId));
        Page<CampaignRecipient> recipients = recipientRepository.findAllByCampaignId(c.getId(), pageable);
        Set<UUID> customerIds = new HashSet<>();
        recipients.forEach(r -> customerIds.add(r.getCustomerId()));
        Map<UUID, Customer> byId = new HashMap<>();
        if (!customerIds.isEmpty()) {
            customerRepository.findAllByIdInAndTenantId(customerIds, tenantId)
                .forEach(cu -> byId.put(cu.getId(), cu));
        }
        return recipients.map(r -> toRecipientDto(r, byId.get(r.getCustomerId())));
    }

    @Transactional
    public CampaignDto update(UUID id, UpdateCampaignRequest request) {
        UUID tenantId = requireTenant();
        Campaign c = campaignRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new CampaignNotFoundException(id));
        if (c.getStatus() != CampaignStatus.DRAFT) {
            throw new InvalidCampaignStateException(
                "Only DRAFT campaigns can be edited (current: " + c.getStatus() + ")");
        }
        if (request.name() != null && !request.name().isBlank()) {
            c.setName(request.name());
        }
        if (request.scheduledAt() != null) {
            c.setScheduledAt(request.scheduledAt());
        }
        return buildDto(c, lookupTemplate(c));
    }

    @Transactional
    public CampaignDto cancel(UUID id) {
        UUID tenantId = requireTenant();
        Campaign c = campaignRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new CampaignNotFoundException(id));
        if (c.getStatus() != CampaignStatus.DRAFT && c.getStatus() != CampaignStatus.SCHEDULED) {
            throw new InvalidCampaignStateException(
                "Cannot cancel a campaign in state " + c.getStatus());
        }
        c.setStatus(CampaignStatus.CANCELLED);
        return buildDto(c, lookupTemplate(c));
    }

    /** Template lookup tolerant of FREE_TEXT campaigns, which carry no templateId. */
    private MessageTemplate lookupTemplate(Campaign c) {
        return c.getTemplateId() == null
            ? null
            : templateRepository.findById(c.getTemplateId()).orElse(null);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = requireTenant();
        Campaign c = campaignRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new CampaignNotFoundException(id));
        // Any campaign can be deleted except one mid-blast — deleting while the worker is still
        // fanning out messages would orphan in-flight queue items. Cancel or wait for it first.
        if (c.getStatus() == CampaignStatus.SENDING) {
            throw new InvalidCampaignStateException(
                "Cannot delete a campaign while it is still SENDING — wait for it to finish");
        }
        // ON DELETE CASCADE on campaign_recipients.campaign_id handles the children.
        campaignRepository.delete(c);
    }

    private CampaignDto buildDto(Campaign c, MessageTemplate template) {
        long total = recipientRepository.countByCampaignId(c.getId());
        long sent = recipientRepository.countByCampaignIdAndStatus(c.getId(), CampaignRecipientStatus.SENT)
            + recipientRepository.countByCampaignIdAndStatus(c.getId(), CampaignRecipientStatus.DELIVERED)
            + recipientRepository.countByCampaignIdAndStatus(c.getId(), CampaignRecipientStatus.READ);
        long failed = recipientRepository.countByCampaignIdAndStatus(c.getId(), CampaignRecipientStatus.FAILED);
        return new CampaignDto(
            c.getId(),
            c.getTenantId(),
            c.getName(),
            c.getStatus(),
            c.getSendMode(),
            c.getTemplateId(),
            template == null ? null : template.getName(),
            c.getBodyText(),
            c.getScheduledAt(),
            c.getCreatedByUserId(),
            c.getStartedAt(),
            c.getCompletedAt(),
            total,
            sent,
            failed,
            c.getCreatedAt(),
            c.getUpdatedAt()
        );
    }

    private static RecipientDto toRecipientDto(CampaignRecipient r, Customer customer) {
        return new RecipientDto(
            r.getId(),
            r.getCampaignId(),
            r.getCustomerId(),
            customer == null ? null : customer.getPhoneE164(),
            customer == null ? null : customer.getFullName(),
            r.getStatus(),
            r.getErrorMessage(),
            r.getSentAt(),
            r.getCreatedAt(),
            r.getUpdatedAt()
        );
    }

    private UUID requireTenant() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("No tenant context — campaigns are tenant-scoped");
        }
        return tenantId;
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            throw new AccessDeniedException("Not authenticated");
        }
        return principal.userId();
    }
}
