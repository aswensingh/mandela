package com.marketinghub.conversation.dto;

import com.marketinghub.conversation.ConversationStatus;
import com.marketinghub.message.MessageDirection;
import com.marketinghub.message.SenderType;

import java.time.Instant;
import java.util.UUID;

public record ConversationListItemDto(
    UUID id,
    ConversationStatus status,
    UUID assignedAgentId,
    UUID customerId,
    String customerPhone,
    String customerName,
    Instant lastMessageAt,
    String lastMessageBody,
    MessageDirection lastMessageDirection,
    SenderType lastMessageSenderType,
    Instant createdAt,
    String handoffReason,
    Double handoffConfidence
) {}
