package com.marketinghub.customer.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BulkDeleteCustomersRequest(
    @NotEmpty(message = "At least one id is required")
    @Size(max = 10000, message = "Cannot delete more than 10000 customers at once")
    List<UUID> ids
) {}
