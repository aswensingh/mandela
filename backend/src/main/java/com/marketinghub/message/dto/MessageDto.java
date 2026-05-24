package com.marketinghub.message.dto;

import com.marketinghub.message.MessageDirection;
import com.marketinghub.message.MessageStatus;
import com.marketinghub.message.SenderType;

import java.time.Instant;
import java.util.UUID;

public record MessageDto(
    UUID id,
    UUID tenantId,
    UUID customerId,
    MessageDirection direction,
    SenderType senderType,
    String body,
    String whatsappMessageId,
    MessageStatus status,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt
) {}
