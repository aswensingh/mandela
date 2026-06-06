package com.marketinghub.tenant;

import com.marketinghub.common.TestToolsGuard;
import com.marketinghub.common.crypto.EncryptionService;
import com.marketinghub.tenant.dto.UpdateWhatsAppConfigRequest;
import com.marketinghub.tenant.dto.WhatsAppConfigStatusDto;
import com.marketinghub.webhook.WhatsAppWebhookService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppConfigServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private WhatsAppWebhookService webhookService;

    private EncryptionService encryption;
    private WhatsAppConfigService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        encryption = new EncryptionService(Base64.getEncoder().encodeToString(new byte[32]));
        encryption.init();
        // Mock=true so testConnection() doesn't try to hit Meta during unrelated tests.
        service = new WhatsAppConfigService(
            tenantRepository, encryption, webhookService,
            new TestToolsGuard(false),
            "https://graph.facebook.com/v21.0", true);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void get_unconfigured_returnsFalseAndNoLastFour() {
        Tenant t = blankTenant();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(t));

        WhatsAppConfigStatusDto status = service.getMyConfig();
        assertThat(status.configured()).isFalse();
        assertThat(status.phoneNumberId()).isNull();
        assertThat(status.tokenLastFour()).isNull();
        assertThat(status.testToolsEnabled()).isFalse();
    }

    @Test
    void update_encryptsTokenAndDoesNotEchoIt() {
        Tenant t = blankTenant();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(t));

        WhatsAppConfigStatusDto status = service.updateMyConfig(
            new UpdateWhatsAppConfigRequest("PHONE123", "EAAJsupersecret1234"));

        assertThat(status.configured()).isTrue();
        assertThat(status.phoneNumberId()).isEqualTo("PHONE123");
        assertThat(status.tokenLastFour()).isEqualTo("1234");
        // The blob stored on the entity is encrypted — must NOT contain the plain token text
        assertThat(new String(t.getWhatsappAccessTokenEncrypted())).doesNotContain("EAAJsupersecret1234");
        // But round-trip decrypt recovers it
        assertThat(encryption.decrypt(t.getWhatsappAccessTokenEncrypted()))
            .isEqualTo("EAAJsupersecret1234");
    }

    @Test
    void get_afterUpdate_returnsConfiguredAndLastFour() {
        Tenant t = blankTenant();
        t.setWhatsappPhoneNumberId("PHONE123");
        t.setWhatsappAccessTokenEncrypted(encryption.encrypt("EAAJexampletoken9876"));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(t));

        WhatsAppConfigStatusDto status = service.getMyConfig();
        assertThat(status.configured()).isTrue();
        assertThat(status.phoneNumberId()).isEqualTo("PHONE123");
        assertThat(status.tokenLastFour()).isEqualTo("9876");
    }

    @Test
    void missingTenantContext_throwsAccessDenied() {
        TenantContext.clear();
        assertThatThrownBy(() -> service.getMyConfig())
            .isInstanceOf(AccessDeniedException.class);
    }

    private Tenant blankTenant() {
        Tenant t = new Tenant();
        t.setId(tenantId);
        t.setName("Test");
        return t;
    }
}
