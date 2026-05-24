package com.marketinghub.tenant;

public enum TenantStatus {
    ACTIVE,
    SUSPENDED,
    // Soft-deleted: hidden from the default UI list, all users blocked from login.
    // Recoverable until a platform admin runs the hard purge.
    DELETED
}
