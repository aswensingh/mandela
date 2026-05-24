package com.marketinghub.user.dto;

import java.util.UUID;

/**
 * The plaintext new password is included in the response so the admin can copy it once and
 * convey it to the user out-of-band. It is never stored (only the BCrypt hash is) and never
 * returned again.
 */
public record ResetPasswordResponse(
    UUID userId,
    String email,
    String newPassword,
    boolean generated
) {}
