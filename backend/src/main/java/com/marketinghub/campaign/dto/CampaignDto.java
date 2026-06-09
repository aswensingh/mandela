package com.marketinghub.campaign.dto;

import com.marketinghub.campaign.CampaignSendMode;
import com.marketinghub.campaign.CampaignStatus;

import java.time.Instant;
import java.util.UUID;

public record CampaignDto(
    UUID id,
    UUID tenantId,
    String name,
    CampaignStatus status,
    CampaignSendMode sendMode,
    UUID templateId,
    String templateName,
    String bodyText,
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
