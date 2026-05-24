package com.marketinghub.campaign.dto;

import com.marketinghub.campaign.CampaignRecipientStatus;

import java.time.Instant;
import java.util.UUID;

public record RecipientDto(
    UUID id,
    UUID campaignId,
    UUID customerId,
    String customerPhone,
    String customerName,
    CampaignRecipientStatus status,
    String errorMessage,
    Instant sentAt,
    Instant createdAt,
    Instant updatedAt
) {}
