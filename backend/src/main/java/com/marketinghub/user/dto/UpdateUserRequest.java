package com.marketinghub.user.dto;

import com.marketinghub.auth.UserRole;
import com.marketinghub.auth.UserStatus;

public record UpdateUserRequest(
    String fullName,
    UserRole role,
    UserStatus status
) {}
