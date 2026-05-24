package com.marketinghub.tenant;

import java.util.UUID;

/**
 * Per-request tenant identifier, populated by TenantContextFilter from the authenticated principal.
 *
 * Platform admins have no tenant — getTenantId() returns null in that case.
 * Services that query tenant-scoped tables must include "tenant_id = :ctx" in their queries
 * (until we revisit Postgres RLS — see ADR-007).
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static UUID getTenantId() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
