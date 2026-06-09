package com.marketinghub.campaign.dto;

import com.marketinghub.campaign.CampaignSendMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Create a campaign in one of two modes (see {@link CampaignSendMode}):
 * <ul>
 *   <li>{@code sendMode = TEMPLATE} (the default when omitted) requires {@code templateId}.</li>
 *   <li>{@code sendMode = FREE_TEXT} requires {@code bodyText}.</li>
 * </ul>
 * The mode-specific "required" check lives in the service (a record can't express
 * conditional validation), which throws a 409 INVALID_CAMPAIGN_STATE when violated.
 */
public record CreateCampaignRequest(
    @NotBlank @Size(max = 200) String name,
    CampaignSendMode sendMode,
    UUID templateId,
    @Size(max = 4096) String bodyText,
    Instant scheduledAt,
    @NotEmpty(message = "At least one customer is required") List<UUID> customerIds
) {}
