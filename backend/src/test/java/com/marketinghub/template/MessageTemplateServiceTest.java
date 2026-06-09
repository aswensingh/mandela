package com.marketinghub.template;

import com.marketinghub.template.dto.CreateTemplateRequest;
import com.marketinghub.template.dto.TemplateDto;
import com.marketinghub.template.dto.UpdateTemplateRequest;
import com.marketinghub.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageTemplateServiceTest {

    @Mock private MessageTemplateRepository templateRepository;

    private MessageTemplateService service;

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MessageTemplateService(templateRepository);
        TenantContext.setTenantId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_savesWithTenantScope_andDefaultStatusPending() {
        when(templateRepository.existsByTenantIdAndWhatsappTemplateNameAndLanguage(
            TENANT_A, "promo_oct", "en_US")).thenReturn(false);
        when(templateRepository.save(any(MessageTemplate.class))).thenAnswer(inv -> {
            MessageTemplate t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        TemplateDto dto = service.create(new CreateTemplateRequest(
            "October promo", "promo_oct", "en_US", "Hi {{1}}, ...", null));

        assertThat(dto.tenantId()).isEqualTo(TENANT_A);
        // Default is PENDING — a template is only APPROVED once Meta confirms it via sync.
        assertThat(dto.status()).isEqualTo(TemplateStatus.PENDING);
        assertThat(dto.whatsappTemplateName()).isEqualTo("promo_oct");

        ArgumentCaptor<MessageTemplate> cap = ArgumentCaptor.forClass(MessageTemplate.class);
        verify(templateRepository).save(cap.capture());
        assertThat(cap.getValue().getTenantId()).isEqualTo(TENANT_A);
    }

    @Test
    void create_dupeNameAndLanguageInTenant_throws() {
        when(templateRepository.existsByTenantIdAndWhatsappTemplateNameAndLanguage(
            TENANT_A, "promo_oct", "en_US")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateTemplateRequest(
            "October promo", "promo_oct", "en_US", null, null)))
            .isInstanceOf(DuplicateTemplateException.class);
        verify(templateRepository, never()).save(any());
    }

    @Test
    void create_requiresTenantContext() {
        TenantContext.clear();
        assertThatThrownBy(() -> service.create(new CreateTemplateRequest(
            "x", "x", "en", null, null)))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void get_otherTenantTemplate_throwsNotFound() {
        UUID otherId = UUID.randomUUID();
        when(templateRepository.findByIdAndTenantId(otherId, TENANT_A)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(otherId))
            .isInstanceOf(TemplateNotFoundException.class);
    }

    @Test
    void update_partial_changesOnlyProvidedFields() {
        UUID id = UUID.randomUUID();
        MessageTemplate existing = stub(id, "promo_oct", "en_US", TemplateStatus.APPROVED);
        existing.setName("Old name");
        existing.setBodyPreview("Old preview");
        when(templateRepository.findByIdAndTenantId(id, TENANT_A)).thenReturn(Optional.of(existing));

        TemplateDto dto = service.update(id, new UpdateTemplateRequest(
            "New name", null, TemplateStatus.PAUSED, null));

        assertThat(dto.name()).isEqualTo("New name");
        assertThat(dto.bodyPreview()).isEqualTo("Old preview"); // not touched
        assertThat(dto.status()).isEqualTo(TemplateStatus.PAUSED);
        assertThat(dto.language()).isEqualTo("en_US"); // not touched
    }

    @Test
    void update_language_correctsCodeWhenNoCollision() {
        UUID id = UUID.randomUUID();
        MessageTemplate existing = stub(id, "promo_oct", "en", TemplateStatus.NOT_FOUND);
        when(templateRepository.findByIdAndTenantId(id, TENANT_A)).thenReturn(Optional.of(existing));
        when(templateRepository.existsByTenantIdAndWhatsappTemplateNameAndLanguage(
            TENANT_A, "promo_oct", "en_US")).thenReturn(false);

        TemplateDto dto = service.update(id, new UpdateTemplateRequest(
            null, null, null, "en_US"));

        assertThat(dto.language()).isEqualTo("en_US");
    }

    @Test
    void update_language_collisionThrows() {
        UUID id = UUID.randomUUID();
        MessageTemplate existing = stub(id, "promo_oct", "en", TemplateStatus.NOT_FOUND);
        when(templateRepository.findByIdAndTenantId(id, TENANT_A)).thenReturn(Optional.of(existing));
        // Another row already uses (promo_oct, en_US).
        when(templateRepository.existsByTenantIdAndWhatsappTemplateNameAndLanguage(
            TENANT_A, "promo_oct", "en_US")).thenReturn(true);

        assertThatThrownBy(() -> service.update(id, new UpdateTemplateRequest(
            null, null, null, "en_US")))
            .isInstanceOf(DuplicateTemplateException.class);
        assertThat(existing.getLanguage()).isEqualTo("en"); // unchanged
    }

    @Test
    void delete_otherTenant_throwsNotFound() {
        UUID otherId = UUID.randomUUID();
        when(templateRepository.findByIdAndTenantId(otherId, TENANT_A)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(otherId))
            .isInstanceOf(TemplateNotFoundException.class);
        verify(templateRepository, never()).delete(any());
    }

    @Test
    void tenantSwitch_changesScope() {
        TenantContext.setTenantId(TENANT_B);
        when(templateRepository.existsByTenantIdAndWhatsappTemplateNameAndLanguage(
            TENANT_B, "promo_oct", "en_US")).thenReturn(false);
        when(templateRepository.save(any(MessageTemplate.class))).thenAnswer(inv -> {
            MessageTemplate t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        TemplateDto dto = service.create(new CreateTemplateRequest(
            "B promo", "promo_oct", "en_US", null, null));

        assertThat(dto.tenantId()).isEqualTo(TENANT_B);
    }

    private static MessageTemplate stub(UUID id, String waName, String lang, TemplateStatus status) {
        MessageTemplate t = new MessageTemplate();
        t.setId(id);
        t.setTenantId(TENANT_A);
        t.setName("stub");
        t.setWhatsappTemplateName(waName);
        t.setLanguage(lang);
        t.setStatus(status);
        return t;
    }
}
