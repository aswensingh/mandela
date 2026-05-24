package com.marketinghub.auth;

import java.util.UUID;

public record AuthenticatedPrincipal(
    UUID userId,
    String email,
    UUID tenantId,
    UserRole role
) {}
