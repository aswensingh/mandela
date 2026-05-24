package com.marketinghub.ai;

import java.util.UUID;

/** Envelope sent on the ai.reply queue after a customer message lands in a BOT_ACTIVE conversation. */
public record AIReplyMessage(
    UUID tenantId,
    UUID conversationId,
    UUID customerId
) {}
