package com.marketinghub.template;

import com.marketinghub.template.dto.CreateTemplateRequest;
import com.marketinghub.template.dto.TemplateDto;
import com.marketinghub.template.dto.UpdateTemplateRequest;
import com.marketinghub.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class MessageTemplateService {

    private final MessageTemplateRepository templateRepository;

    public MessageTemplateService(MessageTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @Transactional(readOnly = true)
    public Page<TemplateDto> list(Pageable pageable) {
        UUID tenantId = requireTenant();
        return templateRepository.findAllByTenantId(tenantId, pageable).map(MessageTemplateService::toDto);
    }

    @Transactional(readOnly = true)
    public TemplateDto get(UUID id) {
        UUID tenantId = requireTenant();
        return templateRepository.findByIdAndTenantId(id, tenantId)
            .map(MessageTemplateService::toDto)
            .orElseThrow(() -> new TemplateNotFoundException(id));
    }

    @Transactional
    public TemplateDto create(CreateTemplateRequest request) {
        UUID tenantId = requireTenant();
        if (templateRepository.existsByTenantIdAndWhatsappTemplateNameAndLanguage(
                tenantId, request.whatsappTemplateName(), request.language())) {
            throw new DuplicateTemplateException(request.whatsappTemplateName(), request.language());
        }
        MessageTemplate t = new MessageTemplate();
        t.setTenantId(tenantId);
        t.setName(request.name());
        t.setWhatsappTemplateName(request.whatsappTemplateName());
        t.setLanguage(request.language());
        t.setBodyPreview(request.bodyPreview());
        // Default to PENDING, not APPROVED: a template only becomes APPROVED once Meta says so
        // (via "Sync from Meta"). Defaulting to APPROVED is what let unapproved templates sail
        // into a launch and fail at Meta with error 132001.
        t.setStatus(request.status() == null ? TemplateStatus.PENDING : request.status());
        MessageTemplate saved = templateRepository.save(t);
        templateRepository.flush();
        return toDto(saved);
    }

    @Transactional
    public TemplateDto update(UUID id, UpdateTemplateRequest request) {
        UUID tenantId = requireTenant();
        MessageTemplate t = templateRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new TemplateNotFoundException(id));
        if (request.name() != null && !request.name().isBlank()) {
            t.setName(request.name());
        }
        if (request.bodyPreview() != null) {
            t.setBodyPreview(request.bodyPreview());
        }
        if (request.status() != null) {
            t.setStatus(request.status());
        }
        // Correcting the language (e.g. en -> en_US) changes the (name, language) identity, so guard
        // the tenant-unique constraint. Status is left as-is — re-run "Sync from Meta" to refresh it.
        if (request.language() != null && !request.language().isBlank()
                && !request.language().equals(t.getLanguage())) {
            if (templateRepository.existsByTenantIdAndWhatsappTemplateNameAndLanguage(
                    tenantId, t.getWhatsappTemplateName(), request.language())) {
                throw new DuplicateTemplateException(t.getWhatsappTemplateName(), request.language());
            }
            t.setLanguage(request.language());
        }
        return toDto(t);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = requireTenant();
        MessageTemplate t = templateRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new TemplateNotFoundException(id));
        templateRepository.delete(t);
    }

    private UUID requireTenant() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("No tenant context — templates are tenant-scoped");
        }
        return tenantId;
    }

    static TemplateDto toDto(MessageTemplate t) {
        return new TemplateDto(
            t.getId(),
            t.getTenantId(),
            t.getName(),
            t.getWhatsappTemplateName(),
            t.getLanguage(),
            t.getBodyPreview(),
            t.getStatus(),
            t.getCreatedAt(),
            t.getUpdatedAt()
        );
    }
}
