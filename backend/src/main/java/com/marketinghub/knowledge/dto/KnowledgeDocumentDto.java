package com.marketinghub.knowledge.dto;

import com.marketinghub.knowledge.KnowledgeDocument;
import com.marketinghub.knowledge.KnowledgeDocumentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code hasContent} = true when the original uploaded bytes are still persisted
 * (everything uploaded post-V14 migration). The UI uses it to enable/disable the
 * Download button — rows from before V14 only have the chunked text in vector_store,
 * not the source file.
 */
public record KnowledgeDocumentDto(
    UUID id,
    UUID tenantId,
    String title,
    String fileName,
    KnowledgeDocumentStatus status,
    String errorMessage,
    int chunkCount,
    UUID createdByUserId,
    boolean hasContent,
    Long contentSize,
    String contentType,
    Instant createdAt,
    Instant updatedAt
) {
    public static KnowledgeDocumentDto of(KnowledgeDocument d) {
        return new KnowledgeDocumentDto(
            d.getId(), d.getTenantId(), d.getTitle(), d.getFileName(),
            d.getStatus(), d.getErrorMessage(), d.getChunkCount(),
            d.getCreatedByUserId(),
            d.getContent() != null && d.getContent().length > 0,
            d.getContentSize(),
            d.getContentType(),
            d.getCreatedAt(), d.getUpdatedAt()
        );
    }
}
