package com.marketinghub.tenant;

import java.util.UUID;

public class TenantNotFoundException extends RuntimeException {
    public TenantNotFoundException(UUID id) {
        super("Tenant not found: " + id);
    }
}
