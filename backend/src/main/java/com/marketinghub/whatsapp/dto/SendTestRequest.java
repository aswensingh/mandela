package com.marketinghub.whatsapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SendTestRequest(
    @NotBlank
    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$",
             message = "must be E.164 (e.g. +12025550100)")
    String toE164,

    @NotBlank @Size(max = 4096) String body
) {}
