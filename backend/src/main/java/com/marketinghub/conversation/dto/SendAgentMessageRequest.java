package com.marketinghub.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendAgentMessageRequest(
    @NotBlank @Size(max = 4096) String body
) {}
