package com.marketinghub.template;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, UUID> {

    Optional<MessageTemplate> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<MessageTemplate> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByTenantIdAndWhatsappTemplateNameAndLanguage(
        UUID tenantId, String whatsappTemplateName, String language);
}
