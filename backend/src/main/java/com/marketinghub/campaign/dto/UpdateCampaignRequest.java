package com.marketinghub.campaign.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateCampaignRequest(
    @Size(max = 200) String name,
    Instant scheduledAt
) {}
