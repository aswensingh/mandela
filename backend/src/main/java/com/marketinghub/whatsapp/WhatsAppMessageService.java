package com.marketinghub.whatsapp;

import com.marketinghub.common.crypto.EncryptionService;
import com.marketinghub.message.Message;
import com.marketinghub.message.MessageDirection;
import com.marketinghub.message.MessageRepository;
import com.marketinghub.message.MessageStatus;
import com.marketinghub.message.SenderType;
import com.marketinghub.message.dto.MessageDto;
import com.marketinghub.tenant.Tenant;
import com.marketinghub.tenant.TenantContext;
import com.marketinghub.tenant.TenantNotFoundException;
import com.marketinghub.tenant.TenantRepository;
import com.marketinghub.whatsapp.dto.SendTestRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
public class WhatsAppMessageService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppMessageService.class);

    private final TenantRepository tenantRepository;
    private final MessageRepository messageRepository;
    private final EncryptionService encryptionService;
    private final WhatsAppApiClient apiClient;
    private final TransactionTemplate tx;

    public WhatsAppMessageService(
        TenantRepository tenantRepository,
        MessageRepository messageRepository,
        EncryptionService encryptionService,
        WhatsAppApiClient apiClient,
        PlatformTransactionManager transactionManager
    ) {
        this.tenantRepository = tenantRepository;
        this.messageRepository = messageRepository;
        this.encryptionService = encryptionService;
        this.apiClient = apiClient;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * Send a one-off "test" WhatsApp message (no customer record).
     *
     * Flow is split across three transactions so the audit trail survives a Meta failure:
     *   tx1 — load tenant config; if not configured, throw
     *   tx2 — insert QUEUED row, returns the new id
     *   network call to Meta (no tx)
     *   tx3 — transition the row to SENT (with wamid) or FAILED (with error message)
     */
    public MessageDto sendTest(SendTestRequest request) {
        UUID tenantId = requireTenant();

        Tenant tenant = tx.execute(s ->
            tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId)));
        if (tenant == null
            || tenant.getWhatsappPhoneNumberId() == null
            || tenant.getWhatsappAccessTokenEncrypted() == null) {
            throw new WhatsAppNotConfiguredException();
        }
        String phoneNumberId = tenant.getWhatsappPhoneNumberId();
        String accessToken = encryptionService.decrypt(tenant.getWhatsappAccessTokenEncrypted());

        UUID messageId = tx.execute(s -> {
            Message m = new Message();
            m.setTenantId(tenantId);
            m.setDirection(MessageDirection.OUT);
            m.setSenderType(SenderType.SYSTEM);
            m.setBody(request.body());
            m.setStatus(MessageStatus.QUEUED);
            messageRepository.save(m);
            messageRepository.flush();
            return m.getId();
        });

        try {
            String wamid = apiClient.sendText(phoneNumberId, accessToken, request.toE164(), request.body());
            Message updated = tx.execute(s -> {
                Message m = messageRepository.findById(messageId).orElseThrow();
                m.setWhatsappMessageId(wamid);
                m.setStatus(MessageStatus.SENT);
                return m;
            });
            return toDto(updated);
        } catch (WhatsAppApiException e) {
            log.warn("Meta send failed for message {}: {}", messageId, e.getMessage());
            tx.executeWithoutResult(s -> {
                Message m = messageRepository.findById(messageId).orElseThrow();
                m.setStatus(MessageStatus.FAILED);
                m.setErrorMessage(truncate(e.getMessage(), 1000));
            });
            throw e;
        }
    }

    private UUID requireTenant() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("No tenant context");
        }
        return tenantId;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static MessageDto toDto(Message m) {
        return new MessageDto(
            m.getId(),
            m.getTenantId(),
            m.getCustomerId(),
            m.getDirection(),
            m.getSenderType(),
            m.getBody(),
            m.getWhatsappMessageId(),
            m.getStatus(),
            m.getErrorMessage(),
            m.getCreatedAt(),
            m.getUpdatedAt()
        );
    }
}
