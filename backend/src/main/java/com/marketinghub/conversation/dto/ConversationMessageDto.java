package com.marketinghub.conversation.dto;

import com.marketinghub.message.MessageDirection;
import com.marketinghub.message.MessageStatus;
import com.marketinghub.message.SenderType;

import java.time.Instant;
import java.util.UUID;

public record ConversationMessageDto(
    UUID id,
    MessageDirection direction,
    SenderType senderType,
    String body,
    MessageStatus status,
    String whatsappMessageId,
    String errorMessage,
    Instant createdAt
) {}
