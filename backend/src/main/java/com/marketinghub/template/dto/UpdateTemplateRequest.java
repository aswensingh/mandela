package com.marketinghub.template.dto;

import com.marketinghub.template.TemplateStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateTemplateRequest(
    @Size(max = 100) String name,
    @Size(max = 4096) String bodyPreview,
    TemplateStatus status,

    // Correct the Meta language code (e.g. en -> en_US) without recreating the template.
    @Size(max = 10)
    @Pattern(regexp = "^[a-z]{2,3}(_[A-Z]{2})?$",
             message = "must look like 'en' or 'en_US'")
    String language
) {}
