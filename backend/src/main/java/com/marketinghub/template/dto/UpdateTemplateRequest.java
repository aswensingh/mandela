package com.marketinghub.template.dto;

import com.marketinghub.template.TemplateStatus;
import jakarta.validation.constraints.Size;

public record UpdateTemplateRequest(
    @Size(max = 100) String name,
    @Size(max = 4096) String bodyPreview,
    TemplateStatus status
) {}
