package com.marketinghub.auth.dto;

import com.marketinghub.auth.UserRole;

import java.util.UUID;

/**
 * User projection returned by /api/auth/login and /api/auth/refresh. {@code tenantName} is
 * null for platform admins (who have no tenant) and otherwise carries the tenant's display
 * name so the UI can show it in the header without an extra round-trip.
 */
public record UserDto(
    UUID id,
    String username,
    String fullName,
    UUID tenantId,
    String tenantName,
    UserRole role
) {}
