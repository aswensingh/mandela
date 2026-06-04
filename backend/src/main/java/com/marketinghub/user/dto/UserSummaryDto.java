package com.marketinghub.user.dto;

import com.marketinghub.auth.UserRole;
import com.marketinghub.auth.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserSummaryDto(
    UUID id,
    UUID tenantId,
    String username,
    String fullName,
    UserRole role,
    UserStatus status,
    Instant lastLoginAt,
    Instant createdAt
) {}
