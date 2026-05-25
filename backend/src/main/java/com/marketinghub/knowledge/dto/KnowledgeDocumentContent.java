package com.marketinghub.knowledge.dto;

/**
 * Service-internal carrier for raw download bytes + metadata needed to build the
 * HTTP response (filename for Content-Disposition, MIME type for Content-Type).
 *
 * Not exposed to the wire — the controller streams `bytes` as the body and uses
 * the other fields to set headers.
 */
public record KnowledgeDocumentContent(
    byte[] bytes,
    String fileName,
    String contentType,
    long size
) {}
