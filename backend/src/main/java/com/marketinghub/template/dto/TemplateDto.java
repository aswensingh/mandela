package com.marketinghub.template.dto;

import com.marketinghub.template.TemplateStatus;

import java.time.Instant;
import java.util.UUID;

public record TemplateDto(
    UUID id,
    UUID tenantId,
    String name,
    String whatsappTemplateName,
    String language,
    String bodyPreview,
    TemplateStatus status,
    Instant createdAt,
    Instant updatedAt
) {}
