package com.marketinghub.tenant.dto;

/**
 * Result of a simulate-inbound call. {@code accepted} is what our webhook
 * service reported (1 means the message was successfully ingested + a
 * conversation created/updated; 0 means the tenant had no matching
 * phone_number_id, which would be a config error).
 */
public record SimulateInboundResponse(
    int accepted,
    String wamid
) {}
