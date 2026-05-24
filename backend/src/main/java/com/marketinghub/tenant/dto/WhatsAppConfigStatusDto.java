package com.marketinghub.tenant.dto;

public record WhatsAppConfigStatusDto(
    boolean configured,
    String phoneNumberId,
    String tokenLastFour
) {}
