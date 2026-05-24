package com.marketinghub.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateWhatsAppConfigRequest(
    @NotBlank String phoneNumberId,
    @NotBlank @Size(min = 8, message = "WhatsApp access token looks too short") String accessToken
) {}
