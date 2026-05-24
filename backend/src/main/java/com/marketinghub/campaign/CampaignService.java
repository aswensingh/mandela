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

        // Template must belong to this tenant.
        MessageTemplate template = templateRepository
            .findByIdAndTenantId(request.templateId(), tenantId)
            .orElseThrow(() -> new TemplateNotFoundException(request.templateId()));

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
        campaign.setTemplateId(template.getId());
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
        // Batch-load template names for every campaign in the page.
        Set<UUID> templateIds = new HashSet<>();
        page.forEach(c -> templateIds.add(c.getTemplateId()));
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
        MessageTemplate t = templateRepository.findById(c.getTemplateId()).orElse(null);
        return buildDto(c, t);
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
        MessageTemplate t = templateRepository.findById(c.getTemplateId()).orElse(null);
        return buildDto(c, t);
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
        MessageTemplate t = templateRepository.findById(c.getTemplateId()).orElse(null);
        return buildDto(c, t);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = requireTenant();
        Campaign c = campaignRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new CampaignNotFoundException(id));
        if (c.getStatus() != CampaignStatus.DRAFT && c.getStatus() != CampaignStatus.CANCELLED) {
            throw new InvalidCampaignStateException(
                "Only DRAFT or CANCELLED campaigns can be deleted (current: " + c.getStatus() + ")");
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
            c.getTemplateId(),
            template == null ? null : template.getName(),
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
