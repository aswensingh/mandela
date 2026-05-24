package com.marketinghub.campaign.dto;

import com.marketinghub.campaign.CampaignStatus;

import java.time.Instant;
import java.util.UUID;

public record CampaignDto(
    UUID id,
    UUID tenantId,
    String name,
    CampaignStatus status,
    UUID templateId,
    String templateName,
    Instant scheduledAt,
    UUID createdByUserId,
    Instant startedAt,
    Instant completedAt,
    long recipientCount,
    long sentCount,
    long failedCount,
    Instant createdAt,
    Instant updatedAt
) {}
