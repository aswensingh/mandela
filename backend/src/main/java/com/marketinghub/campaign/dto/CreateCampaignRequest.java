package com.marketinghub.campaign.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateCampaignRequest(
    @NotBlank @Size(max = 200) String name,
    @NotNull UUID templateId,
    Instant scheduledAt,
    @NotEmpty(message = "At least one customer is required") List<UUID> customerIds
) {}
