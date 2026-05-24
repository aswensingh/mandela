package com.marketinghub.knowledge;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {
    Optional<KnowledgeDocument> findByIdAndTenantId(UUID id, UUID tenantId);
    Page<KnowledgeDocument> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
