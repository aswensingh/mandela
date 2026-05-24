package com.marketinghub.tenant.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body for the hard-purge endpoint. The caller types the tenant's current name to confirm — a
 * typo gate identical to what the UI shows in its confirmation modal.
 */
public record PurgeTenantRequest(
    @NotBlank String confirmName
) {}
