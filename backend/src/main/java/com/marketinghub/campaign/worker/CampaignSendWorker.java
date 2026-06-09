package com.marketinghub.campaign.worker;

import com.marketinghub.campaign.Campaign;
import com.marketinghub.campaign.CampaignRecipient;
import com.marketinghub.campaign.CampaignRecipientRepository;
import com.marketinghub.campaign.CampaignRecipientStatus;
import com.marketinghub.campaign.CampaignRepository;
import com.marketinghub.campaign.CampaignStatus;
import com.marketinghub.common.crypto.EncryptionService;
import com.marketinghub.message.Message;
import com.marketinghub.message.MessageDirection;
import com.marketinghub.message.MessageRepository;
import com.marketinghub.message.MessageStatus;
import com.marketinghub.message.SenderType;
import com.marketinghub.tenant.Tenant;
import com.marketinghub.tenant.TenantRepository;
import com.marketinghub.whatsapp.WhatsAppApiClient;
import com.marketinghub.whatsapp.WhatsAppApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;

/**
 * RabbitMQ consumer that actually delivers each campaign recipient's message.
 *
 *   1. Block on the per-tenant rate limiter
 *   2. Resolve tenant config (decrypt access token unless we're in mock mode)
 *   3. Call WhatsAppApiClient.sendText
 *   4. Persist a messages row + flip the recipient to SENT/FAILED in its own tx
 *   5. If the campaign has no more PENDING recipients, transition it to SENT
 *
 * Exceptions are caught and recorded on the recipient — we don't requeue. That avoids
 * an infinite redelivery loop on permanent failures (bad phone, deleted tenant config).
 */
@Component
public class CampaignSendWorker {

    private static final Logger log = LoggerFactory.getLogger(CampaignSendWorker.class);

    private final TenantRepository tenantRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository recipientRepository;
    private final MessageRepository messageRepository;
    private final EncryptionService encryptionService;
    private final WhatsAppApiClient apiClient;
    private final BlastRateLimiter rateLimiter;
    private final TransactionTemplate tx;

    public CampaignSendWorker(
        TenantRepository tenantRepository,
        CampaignRepository campaignRepository,
        CampaignRecipientRepository recipientRepository,
        MessageRepository messageRepository,
        EncryptionService encryptionService,
        WhatsAppApiClient apiClient,
        BlastRateLimiter rateLimiter,
        PlatformTransactionManager transactionManager
    ) {
        this.tenantRepository = tenantRepository;
        this.campaignRepository = campaignRepository;
        this.recipientRepository = recipientRepository;
        this.messageRepository = messageRepository;
        this.encryptionService = encryptionService;
        this.apiClient = apiClient;
        this.rateLimiter = rateLimiter;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @RabbitListener(queues = AmqpConfig.CAMPAIGN_SEND_QUEUE)
    public void onMessage(CampaignSendMessage envelope) {
        try {
            rateLimiter.acquireBlocking(envelope.tenantId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Worker interrupted while waiting for rate-limit token — recipient {} skipped",
                envelope.recipientId());
            return;
        }

        // Resolve tenant config (real mode needs the encrypted token; mock skips that).
        String phoneNumberId = null;
        String accessToken = null;
        if (!apiClient.isMockMode()) {
            Optional<Tenant> tOpt = tenantRepository.findById(envelope.tenantId());
            if (tOpt.isEmpty()
                || tOpt.get().getWhatsappPhoneNumberId() == null
                || tOpt.get().getWhatsappAccessTokenEncrypted() == null) {
                recordFailure(envelope, "WhatsApp credentials are not configured for this tenant");
                completeCampaignIfDone(envelope.campaignId());
                return;
            }
            phoneNumberId = tOpt.get().getWhatsappPhoneNumberId();
            try {
                accessToken = encryptionService.decrypt(tOpt.get().getWhatsappAccessTokenEncrypted());
            } catch (Exception e) {
                recordFailure(envelope, "Could not decrypt access token: " + e.getMessage());
                completeCampaignIfDone(envelope.campaignId());
                return;
            }
        }

        String wamid = null;
        String failure = null;
        try {
            if (envelope.templateName() != null) {
                // TEMPLATE campaign: approved Meta template — reaches customers outside the 24h window.
                wamid = apiClient.sendTemplate(
                    phoneNumberId, accessToken, envelope.toE164(),
                    envelope.templateName(), envelope.languageCode(), envelope.bodyParams());
            } else {
                // FREE_TEXT campaign: plain text — only delivered inside the 24h window.
                wamid = apiClient.sendText(
                    phoneNumberId, accessToken, envelope.toE164(), envelope.body());
            }
        } catch (WhatsAppApiException e) {
            failure = truncate(e.getMessage(), 1000);
        } catch (Exception e) {
            failure = truncate(e.getClass().getSimpleName() + ": " + e.getMessage(), 1000);
        }

        if (failure == null) {
            recordSuccess(envelope, wamid);
        } else {
            log.warn("Send failed for recipient {} on campaign {}: {}",
                envelope.recipientId(), envelope.campaignId(), failure);
            recordFailure(envelope, failure);
        }
        completeCampaignIfDone(envelope.campaignId());
    }

    private void recordSuccess(CampaignSendMessage envelope, String wamid) {
        tx.executeWithoutResult(s -> {
            Instant now = Instant.now();
            Message m = new Message();
            m.setTenantId(envelope.tenantId());
            m.setCustomerId(envelope.customerId());
            m.setDirection(MessageDirection.OUT);
            m.setSenderType(SenderType.SYSTEM);
            m.setBody(envelope.body());
            m.setWhatsappMessageId(wamid);
            m.setStatus(MessageStatus.SENT);
            messageRepository.save(m);

            CampaignRecipient r = recipientRepository.findById(envelope.recipientId()).orElse(null);
            if (r != null) {
                r.setStatus(CampaignRecipientStatus.SENT);
                r.setSentAt(now);
                // Link the wamid so a later Meta delivery-status webhook can update this
                // recipient (DELIVERED/READ/FAILED) — see WhatsAppWebhookService.handleStatus.
                r.setWhatsappMessageId(wamid);
            }
        });
    }

    private void recordFailure(CampaignSendMessage envelope, String reason) {
        tx.executeWithoutResult(s -> {
            Message m = new Message();
            m.setTenantId(envelope.tenantId());
            m.setCustomerId(envelope.customerId());
            m.setDirection(MessageDirection.OUT);
            m.setSenderType(SenderType.SYSTEM);
            m.setBody(envelope.body());
            m.setStatus(MessageStatus.FAILED);
            m.setErrorMessage(reason);
            messageRepository.save(m);

            CampaignRecipient r = recipientRepository.findById(envelope.recipientId()).orElse(null);
            if (r != null) {
                r.setStatus(CampaignRecipientStatus.FAILED);
                r.setErrorMessage(reason);
            }
        });
    }

    private void completeCampaignIfDone(java.util.UUID campaignId) {
        tx.executeWithoutResult(s -> {
            long pending = recipientRepository.countByCampaignIdAndStatus(
                campaignId, CampaignRecipientStatus.PENDING);
            if (pending > 0) return;
            Campaign c = campaignRepository.findById(campaignId).orElse(null);
            if (c == null) return;
            if (c.getStatus() == CampaignStatus.SENDING) {
                c.setStatus(CampaignStatus.SENT);
                c.setCompletedAt(Instant.now());
                log.info("Campaign {} completed", campaignId);
            }
        });
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
