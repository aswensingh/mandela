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
import com.marketinghub.tenant.TenantRepository;
import com.marketinghub.whatsapp.dto.SendTestRequest;
import org.junit.jupiter.api.AfterEach;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppMessageServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private WhatsAppApiClient apiClient;

    private final PlatformTransactionManager noopTm = new PlatformTransactionManager() {
        @Override public TransactionStatus getTransaction(TransactionDefinition def) { return new SimpleTransactionStatus(); }
        @Override public void commit(TransactionStatus s) {}
        @Override public void rollback(TransactionStatus s) {}
    };

    private EncryptionService encryption;
    private WhatsAppMessageService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        encryption = new EncryptionService(Base64.getEncoder().encodeToString(new byte[32]));
        encryption.init();
        service = new WhatsAppMessageService(
            tenantRepository, messageRepository, encryption, apiClient, noopTm);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void sendTest_happyPath_storesSentRow() {
        Tenant t = configuredTenant();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(t));
        AtomicReference<Message> saved = new AtomicReference<>();
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            saved.set(m);
            return m;
        });
        when(messageRepository.findById(any(UUID.class))).thenAnswer(inv -> Optional.of(saved.get()));
        when(apiClient.sendText(eq("PHN123"), anyString(), eq("+14155550100"), eq("hello")))
            .thenReturn("wamid.HBgM_test123");

        MessageDto dto = service.sendTest(new SendTestRequest("+14155550100", "hello"));

        assertThat(dto.status()).isEqualTo(MessageStatus.SENT);
        assertThat(dto.whatsappMessageId()).isEqualTo("wamid.HBgM_test123");
        assertThat(dto.direction()).isEqualTo(MessageDirection.OUT);
        assertThat(dto.senderType()).isEqualTo(SenderType.SYSTEM);
        assertThat(dto.tenantId()).isEqualTo(tenantId);
        assertThat(dto.customerId()).isNull();
        assertThat(dto.errorMessage()).isNull();
    }

    @Test
    void sendTest_decryptsTokenBeforeCallingMeta() {
        Tenant t = configuredTenant();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(t));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(messageRepository.findById(any(UUID.class))).thenAnswer(inv -> {
            Message m = new Message();
            m.setId(inv.getArgument(0));
            m.setStatus(MessageStatus.QUEUED);
            return Optional.of(m);
        });
        when(apiClient.sendText(anyString(), anyString(), anyString(), anyString())).thenReturn("wamid.x");

        service.sendTest(new SendTestRequest("+14155550100", "hi"));

        ArgumentCaptor<String> tokenCap = ArgumentCaptor.forClass(String.class);
        verify(apiClient).sendText(eq("PHN123"), tokenCap.capture(), eq("+14155550100"), eq("hi"));
        assertThat(tokenCap.getValue()).isEqualTo("EAAJsecretTOKEN-1234");
    }

    @Test
    void sendTest_metaFailure_marksMessageFAILED_andRethrows() {
        Tenant t = configuredTenant();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(t));
        AtomicReference<Message> saved = new AtomicReference<>();
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            saved.set(m);
            return m;
        });
        when(messageRepository.findById(any(UUID.class))).thenAnswer(inv -> Optional.of(saved.get()));
        when(apiClient.sendText(anyString(), anyString(), anyString(), anyString()))
            .thenThrow(new WhatsAppApiException("Meta API 400: bad to"));

        assertThatThrownBy(() -> service.sendTest(new SendTestRequest("+14155550100", "hi")))
            .isInstanceOf(WhatsAppApiException.class);

        // Audit trail preserved: the entity ended up FAILED with the error captured
        assertThat(saved.get().getStatus()).isEqualTo(MessageStatus.FAILED);
        assertThat(saved.get().getErrorMessage()).contains("Meta API 400");
        assertThat(saved.get().getWhatsappMessageId()).isNull();
    }

    @Test
    void sendTest_throwsNotConfiguredWhenNoCreds_andDoesNotInsertRow() {
        Tenant unconfigured = new Tenant();
        unconfigured.setId(tenantId);
        unconfigured.setName("Unconfigured");
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(unconfigured));

        assertThatThrownBy(() -> service.sendTest(new SendTestRequest("+14155550100", "hi")))
            .isInstanceOf(WhatsAppNotConfiguredException.class);

        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendTest_partialConfig_alsoTreatedAsNotConfigured() {
        Tenant t = new Tenant();
        t.setId(tenantId);
        t.setName("Half");
        t.setWhatsappPhoneNumberId("PHN123"); // no token
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.sendTest(new SendTestRequest("+14155550100", "hi")))
            .isInstanceOf(WhatsAppNotConfiguredException.class);
    }

    private Tenant configuredTenant() {
        Tenant t = new Tenant();
        t.setId(tenantId);
        t.setName("Configured");
        t.setWhatsappPhoneNumberId("PHN123");
        t.setWhatsappAccessTokenEncrypted(encryption.encrypt("EAAJsecretTOKEN-1234"));
        return t;
    }
}
