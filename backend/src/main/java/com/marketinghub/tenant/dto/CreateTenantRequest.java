package com.marketinghub.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
    @NotBlank String name,
    String industry,
    @NotBlank String initialAdminUsername,
    @NotBlank @Size(min = 8) String initialAdminPassword
) {}
