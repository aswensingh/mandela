package com.marketinghub.tenant.dto;

import java.util.UUID;

/**
 * Minimal admin projection embedded inside TenantDto — surfaces the admin's id (so the UI
 * can target the reset-password endpoint) and username (so the platform admin knows who they're
 * resetting). We deliberately do NOT include status / role / lastLoginAt here — that lives on
 * the Users page when (later) the platform admin drills into a tenant.
 */
public record TenantAdminDto(
    UUID id,
    String username
) {}
