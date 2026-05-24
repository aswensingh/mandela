package com.marketinghub.tenant.dto;

import com.marketinghub.auth.dto.UserDto;

public record CreateTenantResponse(
    TenantDto tenant,
    UserDto initialAdmin
) {}
