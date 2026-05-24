package com.marketinghub.user.dto;

import jakarta.validation.constraints.Size;

/**
 * Body for the reset-password endpoint. {@code newPassword} is optional — when null or blank,
 * the server generates a 16-character random password and returns it in the response.
 */
public record ResetPasswordRequest(
    @Size(max = 128, message = "Password must be at most 128 characters")
    String newPassword
) {}
