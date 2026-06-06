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
import com.marketinghub.tenant.Tenant;
import com.marketinghub.tenant.TenantRepository;
import com.marketinghub.whatsapp.WhatsAppApiClient;
import com.marketinghub.whatsapp.WhatsAppApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignSendWorkerTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignRecipientRepository recipientRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private WhatsAppApiClient apiClient;
    @Mock private BlastRateLimiter rateLimiter;

    private final PlatformTransactionManager noopTm = new PlatformTransactionManager() {
        @Override public TransactionStatus getTransaction(TransactionDefinition def) { return new SimpleTransactionStatus(); }
        @Override public void commit(TransactionStatus s) {}
        @Override public void rollback(TransactionStatus s) {}
    };

    private EncryptionService encryption;
    private CampaignSendWorker worker;

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID CAMPAIGN = UUID.randomUUID();
    private static final UUID RECIPIENT = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID TEMPLATE = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        encryption = new EncryptionService(Base64.getEncoder().encodeToString(new byte[32]));
        encryption.init();
        worker = new CampaignSendWorker(
            tenantRepository,
            campaignRepository,
            recipientRepository,
            messageRepository,
            encryption,
            apiClient,
            rateLimiter,
            noopTm
        );
    }

    @Test
    void mockMode_happyPath_recordsSentAndAdvancesCampaign() throws Exception {
        when(apiClient.isMockMode()).thenReturn(true);
        when(apiClient.sendTemplate(any(), any(), eq("+14155550100"), any(), any(), any()))
            .thenReturn("wamid.MOCK-abc");

        AtomicReference<Message> savedMessage = new AtomicReference<>();
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            savedMessage.set(m);
            return m;
        });

        CampaignRecipient recipient = stubRecipient(RECIPIENT, CAMPAIGN, CUSTOMER);
        when(recipientRepository.findById(RECIPIENT)).thenReturn(Optional.of(recipient));

        // All other recipients already processed -> last one in the campaign.
        when(recipientRepository.countByCampaignIdAndStatus(CAMPAIGN, CampaignRecipientStatus.PENDING))
            .thenReturn(0L);
        Campaign campaign = stubCampaign(CAMPAIGN, CampaignStatus.SENDING);
        when(campaignRepository.findById(CAMPAIGN)).thenReturn(Optional.of(campaign));

        worker.onMessage(envelope("+14155550100", "Hello"));

        verify(rateLimiter).acquireBlocking(TENANT);
        // Mock mode: tenant lookup is skipped entirely
        verify(tenantRepository, never()).findById(any());

        assertThat(savedMessage.get()).isNotNull();
        assertThat(savedMessage.get().getStatus()).isEqualTo(MessageStatus.SENT);
        assertThat(savedMessage.get().getWhatsappMessageId()).isEqualTo("wamid.MOCK-abc");
        assertThat(savedMessage.get().getDirection()).isEqualTo(MessageDirection.OUT);
        assertThat(savedMessage.get().getTenantId()).isEqualTo(TENANT);
        assertThat(recipient.getStatus()).isEqualTo(CampaignRecipientStatus.SENT);
        assertThat(recipient.getSentAt()).isNotNull();
        // Last recipient -> campaign completes
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.SENT);
        assertThat(campaign.getCompletedAt()).isNotNull();
    }

    @Test
    void mockMode_failure_recordsFailedRowAndAdvancesCampaign() throws Exception {
        when(apiClient.isMockMode()).thenReturn(true);
        when(apiClient.sendTemplate(any(), any(), anyString(), any(), any(), any()))
            .thenThrow(new WhatsAppApiException("Meta API 400: nope"));

        AtomicReference<Message> savedMessage = new AtomicReference<>();
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            savedMessage.set(m);
            return m;
        });

        CampaignRecipient recipient = stubRecipient(RECIPIENT, CAMPAIGN, CUSTOMER);
        when(recipientRepository.findById(RECIPIENT)).thenReturn(Optional.of(recipient));
        when(recipientRepository.countByCampaignIdAndStatus(CAMPAIGN, CampaignRecipientStatus.PENDING))
            .thenReturn(0L);
        Campaign campaign = stubCampaign(CAMPAIGN, CampaignStatus.SENDING);
        when(campaignRepository.findById(CAMPAIGN)).thenReturn(Optional.of(campaign));

        worker.onMessage(envelope("+14155550100", "Hello"));

        assertThat(savedMessage.get().getStatus()).isEqualTo(MessageStatus.FAILED);
        assertThat(savedMessage.get().getErrorMessage()).contains("Meta API 400");
        assertThat(savedMessage.get().getWhatsappMessageId()).isNull();
        assertThat(recipient.getStatus()).isEqualTo(CampaignRecipientStatus.FAILED);
        assertThat(recipient.getErrorMessage()).contains("Meta API 400");
        // Campaign still transitions to SENT once every recipient has settled (some failed, some sent).
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.SENT);
    }

    @Test
    void realMode_missingTenantConfig_marksRecipientFailed() throws Exception {
        when(apiClient.isMockMode()).thenReturn(false);
        Tenant t = new Tenant();
        t.setId(TENANT);
        t.setName("No-creds");
        // No whatsappPhoneNumberId / encrypted token set
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(t));

        ArgumentCaptor<Message> messageCap = ArgumentCaptor.forClass(Message.class);
        when(messageRepository.save(messageCap.capture())).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        CampaignRecipient recipient = stubRecipient(RECIPIENT, CAMPAIGN, CUSTOMER);
        when(recipientRepository.findById(RECIPIENT)).thenReturn(Optional.of(recipient));
        when(recipientRepository.countByCampaignIdAndStatus(CAMPAIGN, CampaignRecipientStatus.PENDING))
            .thenReturn(5L); // more recipients still pending — campaign stays SENDING

        worker.onMessage(envelope("+14155550100", "Hi"));

        verify(apiClient, never()).sendTemplate(any(), any(), any(), any(), any(), any());
        assertThat(messageCap.getValue().getStatus()).isEqualTo(MessageStatus.FAILED);
        assertThat(recipient.getStatus()).isEqualTo(CampaignRecipientStatus.FAILED);
        assertThat(recipient.getErrorMessage()).contains("credentials");
    }

    @Test
    void doesNotCompleteCampaign_whenSomePendingRemain() throws Exception {
        when(apiClient.isMockMode()).thenReturn(true);
        when(apiClient.sendTemplate(any(), any(), anyString(), any(), any(), any())).thenReturn("wamid.MOCK-x");
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignRecipient recipient = stubRecipient(RECIPIENT, CAMPAIGN, CUSTOMER);
        when(recipientRepository.findById(RECIPIENT)).thenReturn(Optional.of(recipient));
        when(recipientRepository.countByCampaignIdAndStatus(CAMPAIGN, CampaignRecipientStatus.PENDING))
            .thenReturn(7L);

        worker.onMessage(envelope("+14155550100", "Hi"));

        // Campaign isn't queried because pending > 0 — verify by never asking for it.
        verify(campaignRepository, never()).findById(any());
    }

    private static CampaignRecipient stubRecipient(UUID id, UUID campaignId, UUID customerId) {
        CampaignRecipient r = new CampaignRecipient();
        r.setId(id);
        r.setCampaignId(campaignId);
        r.setCustomerId(customerId);
        r.setStatus(CampaignRecipientStatus.PENDING);
        return r;
    }

    private static Campaign stubCampaign(UUID id, CampaignStatus status) {
        Campaign c = new Campaign();
        c.setId(id);
        c.setTenantId(TENANT);
        c.setName("Stub");
        c.setStatus(status);
        c.setTemplateId(TEMPLATE);
        return c;
    }

    private static CampaignSendMessage envelope(String to, String body) {
        return new CampaignSendMessage(
            TENANT, CAMPAIGN, RECIPIENT, CUSTOMER, TEMPLATE, to, body,
            "promo_template", "en", List.of("Jane"));
    }
}
