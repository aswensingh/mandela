package com.marketinghub.knowledge.dto;

import com.marketinghub.knowledge.KnowledgeDocument;
import com.marketinghub.knowledge.KnowledgeDocumentStatus;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeDocumentDto(
    UUID id,
    UUID tenantId,
    String title,
    String fileName,
    KnowledgeDocumentStatus status,
    String errorMessage,
    int chunkCount,
    UUID createdByUserId,
    Instant createdAt,
    Instant updatedAt
) {
    public static KnowledgeDocumentDto of(KnowledgeDocument d) {
        return new KnowledgeDocumentDto(
            d.getId(), d.getTenantId(), d.getTitle(), d.getFileName(),
            d.getStatus(), d.getErrorMessage(), d.getChunkCount(),
            d.getCreatedByUserId(), d.getCreatedAt(), d.getUpdatedAt()
        );
    }
}
