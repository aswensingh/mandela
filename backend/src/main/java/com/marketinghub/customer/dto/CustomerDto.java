package com.marketinghub.customer.dto;

import com.marketinghub.customer.OptInStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CustomerDto(
    UUID id,
    UUID tenantId,
    String phoneE164,
    String fullName,
    List<String> tags,
    OptInStatus optInStatus,
    Map<String, Object> customAttributes,
    Instant createdAt,
    Instant updatedAt
) {}
