package com.marketinghub.campaign.worker;

import java.util.UUID;

/** Envelope sent on the campaign.send queue for each recipient. */
public record CampaignSendMessage(
    UUID tenantId,
    UUID campaignId,
    UUID recipientId,
    UUID customerId,
    UUID templateId,
    String toE164,
    String body
) {}
