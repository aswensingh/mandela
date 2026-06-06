package com.marketinghub.campaign.worker;

import java.util.List;
import java.util.UUID;

/**
 * Envelope sent on the campaign.send queue for each recipient.
 *
 * Campaigns are delivered as approved WhatsApp <b>template</b> messages (so they reach
 * customers outside the 24h window). {@code templateName} + {@code languageCode} identify the
 * Meta-approved template and {@code bodyParams} fill its {{1}}, {{2}}, ... variables in order.
 * {@code body} is the rendered preview text we persist for the in-app conversation view.
 */
public record CampaignSendMessage(
    UUID tenantId,
    UUID campaignId,
    UUID recipientId,
    UUID customerId,
    UUID templateId,
    String toE164,
    String body,
    String templateName,
    String languageCode,
    List<String> bodyParams
) {}
