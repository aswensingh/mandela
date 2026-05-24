package com.marketinghub.customer.dto;

import com.marketinghub.customer.OptInStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.Map;

public record CreateCustomerRequest(
    @NotBlank
    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$",
             message = "must be E.164 (e.g. +12025550100)")
    String phoneE164,

    String fullName,
    List<String> tags,
    OptInStatus optInStatus,
    Map<String, Object> customAttributes
) {}
