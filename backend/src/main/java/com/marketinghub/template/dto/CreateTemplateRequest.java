package com.marketinghub.template.dto;

import com.marketinghub.template.TemplateStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTemplateRequest(
    @NotBlank @Size(max = 100) String name,

    @NotBlank @Size(max = 100)
    @Pattern(regexp = "^[a-z0-9_]+$",
             message = "must be lowercase letters, digits, or underscores (Meta convention)")
    String whatsappTemplateName,

    @NotBlank @Size(max = 10)
    @Pattern(regexp = "^[a-z]{2,3}(_[A-Z]{2})?$",
             message = "must look like 'en' or 'en_US'")
    String language,

    @Size(max = 4096) String bodyPreview,

    TemplateStatus status
) {}
