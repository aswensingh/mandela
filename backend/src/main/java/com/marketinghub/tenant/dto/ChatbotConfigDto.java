package com.marketinghub.tenant.dto;

public record ChatbotConfigDto(
    String aiSystemPrompt,
    Double handoffConfidenceThreshold
) {}
