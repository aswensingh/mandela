package com.marketinghub.auth.dto;

import com.marketinghub.auth.UserRole;

import java.util.UUID;

public record UserDto(
    UUID id,
    String email,
    String fullName,
    UUID tenantId,
    UserRole role
) {}
