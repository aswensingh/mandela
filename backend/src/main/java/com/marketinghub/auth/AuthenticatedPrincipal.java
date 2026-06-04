package com.marketinghub.auth;

import java.util.UUID;

public record AuthenticatedPrincipal(
    UUID userId,
    String username,
    UUID tenantId,
    UserRole role
) {}
