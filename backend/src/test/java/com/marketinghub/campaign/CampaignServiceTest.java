package com.marketinghub.campaign;

import com.marketinghub.auth.AuthenticatedPrincipal;
import com.marketinghub.auth.UserRole;
import com.marketinghub.campaign.dto.CampaignDto;
import com.marketinghub.campaign.dto.CreateCampaignRequest;
import com.marketinghub.campaign.dto.UpdateCampaignRequest;
import com.marketinghub.customer.CustomerRepository;
import com.marketinghub.template.MessageTemplate;
import com.marketinghub.template.MessageTemplateRepository;
import com.marketinghub.template.TemplateNotFoundException;
import com.marketinghub.template.TemplateStatus;
import com.marketinghub.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignRecipientRepository recipientRepository;
    @Mock private MessageTemplateRepository templateRepository;
    @Mock private CustomerRepository customerRepository;

    private CampaignService service;

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CampaignService(
            campaignRepository, recipientRepository, templateRepository, customerRepository);
        TenantContext.setTenantId(TENANT_A);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedPrincipal(USER_ID, "u@a.com", TENANT_A, UserRole.TENANT_ADMIN),
                null,
                List.of()
            )
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_savesCampaignAndOneRecipientPerCustomer() {
        UUID templateId = UUID.randomUUID();
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        UUID c3 = UUID.randomUUID();
        MessageTemplate t = stubTemplate(templateId, TENANT_A);
        when(templateRepository.findByIdAndTenantId(templateId, TENANT_A))
            .thenReturn(Optional.of(t));
        when(customerRepository.countByIdInAndTenantId(Set.of(c1, c2, c3), TENANT_A)).thenReturn(3L);
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> {
            Campaign c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(recipientRepository.save(any(CampaignRecipient.class))).thenAnswer(inv -> {
            CampaignRecipient r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(recipientRepository.countByCampaignId(any(UUID.class))).thenReturn(3L);

        CampaignDto dto = service.create(new CreateCampaignRequest(
            "Spring blast", CampaignSendMode.TEMPLATE, templateId, null, null, List.of(c1, c2, c3)));

        assertThat(dto.tenantId()).isEqualTo(TENANT_A);
        assertThat(dto.status()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(dto.sendMode()).isEqualTo(CampaignSendMode.TEMPLATE);
        assertThat(dto.templateId()).isEqualTo(templateId);
        assertThat(dto.templateName()).isEqualTo("My template");
        assertThat(dto.recipientCount()).isEqualTo(3);
        verify(recipientRepository, org.mockito.Mockito.times(3)).save(any(CampaignRecipient.class));
    }

    @Test
    void create_dedupsRepeatedCustomerIds() {
        UUID templateId = UUID.randomUUID();
        UUID c1 = UUID.randomUUID();
        when(templateRepository.findByIdAndTenantId(templateId, TENANT_A))
            .thenReturn(Optional.of(stubTemplate(templateId, TENANT_A)));
        // Dedup makes the set {c1} so the count must equal 1.
        when(customerRepository.countByIdInAndTenantId(Set.of(c1), TENANT_A)).thenReturn(1L);
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> {
            Campaign c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(recipientRepository.save(any(CampaignRecipient.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(new CreateCampaignRequest(
            "Dedup test", CampaignSendMode.TEMPLATE, templateId, null, null, List.of(c1, c1, c1)));

        // Three duplicates collapse to one save call.
        verify(recipientRepository, org.mockito.Mockito.times(1)).save(any(CampaignRecipient.class));
    }

    @Test
    void create_templateFromOtherTenant_throws() {
        UUID templateId = UUID.randomUUID();
        when(templateRepository.findByIdAndTenantId(templateId, TENANT_A))
            .thenReturn(Optional.empty()); // template belongs to TENANT_B

        assertThatThrownBy(() -> service.create(new CreateCampaignRequest(
            "x", CampaignSendMode.TEMPLATE, templateId, null, null, List.of(UUID.randomUUID()))))
            .isInstanceOf(TemplateNotFoundException.class);

        verify(campaignRepository, never()).save(any());
        verify(recipientRepository, never()).save(any());
    }

    @Test
    void create_customerFromOtherTenant_throws() {
        UUID templateId = UUID.randomUUID();
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        when(templateRepository.findByIdAndTenantId(templateId, TENANT_A))
            .thenReturn(Optional.of(stubTemplate(templateId, TENANT_A)));
        // Only one of the two belongs to TENANT_A.
        when(customerRepository.countByIdInAndTenantId(Set.of(c1, c2), TENANT_A)).thenReturn(1L);

        assertThatThrownBy(() -> service.create(new CreateCampaignRequest(
            "x", CampaignSendMode.TEMPLATE, templateId, null, null, List.of(c1, c2))))
            .isInstanceOf(InvalidCampaignStateException.class);

        verify(campaignRepository, never()).save(any());
    }

    @Test
    void get_otherTenant_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(campaignRepository.findByIdAndTenantId(id, TENANT_A)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id))
            .isInstanceOf(CampaignNotFoundException.class);
    }

    @Test
    void update_onlyAllowedInDraft() {
        UUID id = UUID.randomUUID();
        Campaign c = stubCampaign(id, TENANT_A);
        c.setStatus(CampaignStatus.SENDING);
        when(campaignRepository.findByIdAndTenantId(id, TENANT_A)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.update(id, new UpdateCampaignRequest("new name", null)))
            .isInstanceOf(InvalidCampaignStateException.class);
    }

    @Test
    void cancel_movesDraftToCancelled() {
        UUID id = UUID.randomUUID();
        Campaign c = stubCampaign(id, TENANT_A);
        when(campaignRepository.findByIdAndTenantId(id, TENANT_A)).thenReturn(Optional.of(c));
        when(templateRepository.findById(c.getTemplateId())).thenReturn(Optional.of(
            stubTemplate(c.getTemplateId(), TENANT_A)));
        when(recipientRepository.countByCampaignId(id)).thenReturn(0L);

        CampaignDto dto = service.cancel(id);
        assertThat(dto.status()).isEqualTo(CampaignStatus.CANCELLED);
    }

    @Test
    void cancel_fromSentIsRejected() {
        UUID id = UUID.randomUUID();
        Campaign c = stubCampaign(id, TENANT_A);
        c.setStatus(CampaignStatus.SENT);
        when(campaignRepository.findByIdAndTenantId(id, TENANT_A)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.cancel(id))
            .isInstanceOf(InvalidCampaignStateException.class);
    }

    @Test
    void delete_blockedWhileSending() {
        UUID id = UUID.randomUUID();
        Campaign c = stubCampaign(id, TENANT_A);
        c.setStatus(CampaignStatus.SENDING);
        when(campaignRepository.findByIdAndTenantId(id, TENANT_A)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.delete(id))
            .isInstanceOf(InvalidCampaignStateException.class);
        verify(campaignRepository, never()).delete(any());
    }

    @Test
    void delete_allowedWhenSent() {
        UUID id = UUID.randomUUID();
        Campaign c = stubCampaign(id, TENANT_A);
        c.setStatus(CampaignStatus.SENT);
        when(campaignRepository.findByIdAndTenantId(id, TENANT_A)).thenReturn(Optional.of(c));

        service.delete(id);

        verify(campaignRepository).delete(c);
    }

    @Test
    void tenantSwitch_isolates() {
        UUID templateId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        TenantContext.setTenantId(TENANT_B);
        when(templateRepository.findByIdAndTenantId(eq(templateId), eq(TENANT_B)))
            .thenReturn(Optional.of(stubTemplate(templateId, TENANT_B)));
        when(customerRepository.countByIdInAndTenantId(Set.of(customerId), TENANT_B)).thenReturn(1L);
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> {
            Campaign c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(recipientRepository.save(any(CampaignRecipient.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignDto dto = service.create(new CreateCampaignRequest(
            "B blast", CampaignSendMode.TEMPLATE, templateId, null, null, List.of(customerId)));
        assertThat(dto.tenantId()).isEqualTo(TENANT_B);
    }

    @Test
    void create_freeText_savesBodyAndNoTemplate() {
        UUID c1 = UUID.randomUUID();
        when(customerRepository.countByIdInAndTenantId(Set.of(c1), TENANT_A)).thenReturn(1L);
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> {
            Campaign c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(recipientRepository.save(any(CampaignRecipient.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignDto dto = service.create(new CreateCampaignRequest(
            "Flash sale", CampaignSendMode.FREE_TEXT, null, "Hi {{name}}, flash sale today!",
            null, List.of(c1)));

        assertThat(dto.sendMode()).isEqualTo(CampaignSendMode.FREE_TEXT);
        assertThat(dto.templateId()).isNull();
        assertThat(dto.templateName()).isNull();
        assertThat(dto.bodyText()).isEqualTo("Hi {{name}}, flash sale today!");
        // FREE_TEXT must never touch the template repository.
        verify(templateRepository, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    void create_freeText_withoutBody_throws() {
        assertThatThrownBy(() -> service.create(new CreateCampaignRequest(
            "No body", CampaignSendMode.FREE_TEXT, null, "  ", null, List.of(UUID.randomUUID()))))
            .isInstanceOf(InvalidCampaignStateException.class);
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void create_template_withoutTemplateId_throws() {
        assertThatThrownBy(() -> service.create(new CreateCampaignRequest(
            "No template", CampaignSendMode.TEMPLATE, null, null, null, List.of(UUID.randomUUID()))))
            .isInstanceOf(InvalidCampaignStateException.class);
        verify(campaignRepository, never()).save(any());
    }

    private static MessageTemplate stubTemplate(UUID id, UUID tenantId) {
        MessageTemplate t = new MessageTemplate();
        t.setId(id);
        t.setTenantId(tenantId);
        t.setName("My template");
        t.setWhatsappTemplateName("my_template");
        t.setLanguage("en_US");
        t.setStatus(TemplateStatus.APPROVED);
        return t;
    }

    private static Campaign stubCampaign(UUID id, UUID tenantId) {
        Campaign c = new Campaign();
        c.setId(id);
        c.setTenantId(tenantId);
        c.setName("Existing");
        c.setStatus(CampaignStatus.DRAFT);
        c.setTemplateId(UUID.randomUUID());
        c.setCreatedByUserId(USER_ID);
        return c;
    }
}
