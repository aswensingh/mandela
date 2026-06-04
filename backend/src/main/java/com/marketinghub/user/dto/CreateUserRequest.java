package com.marketinghub.user.dto;

import com.marketinghub.auth.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
    @NotBlank String username,
    @NotBlank @Size(min = 8) String password,
    @NotBlank String fullName,
    @NotNull UserRole role
) {}
