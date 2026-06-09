package com.marketinghub.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateWhatsAppConfigRequest(
    @NotBlank String phoneNumberId,
    @NotBlank @Size(min = 8, message = "WhatsApp access token looks too short") String accessToken,
    // Optional: the WhatsApp Business Account (WABA) ID. Needed only for syncing template
    // approval status from Meta — sends work without it. Blank/null is fine.
    String businessAccountId
) {}
