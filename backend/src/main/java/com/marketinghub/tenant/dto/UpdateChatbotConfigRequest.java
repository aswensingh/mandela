package com.marketinghub.tenant.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

public record UpdateChatbotConfigRequest(
    @Size(max = 8000) String aiSystemPrompt,

    @DecimalMin(value = "0.0", message = "must be between 0 and 1")
    @DecimalMax(value = "1.0", message = "must be between 0 and 1")
    Double handoffConfidenceThreshold
) {}
