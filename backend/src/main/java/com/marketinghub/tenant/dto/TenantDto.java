package com.marketinghub.tenant.dto;

import com.marketinghub.tenant.TenantStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tenant projection for the Tenants list. {@code admins} is the list of users with role
 * TENANT_ADMIN in this tenant, surfaced so the Platform Admin can see at a glance who owns
 * each tenant and reset their password.
 */
public record TenantDto(
    UUID id,
    String name,
    String industry,
    TenantStatus status,
    Instant createdAt,
    Instant updatedAt,
    List<TenantAdminDto> admins
) {}
