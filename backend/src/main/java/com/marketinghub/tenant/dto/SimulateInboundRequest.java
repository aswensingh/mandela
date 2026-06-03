package com.marketinghub.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body for POST /api/tenants/me/whatsapp/simulate-inbound — fakes a customer
 * message hitting our webhook with the tenant's own phone_number_id, so the
 * tenant admin can end-to-end test the receive → AI reply → outbound loop
 * without depending on Meta's flaky dev-mode webhook delivery.
 */
public record SimulateInboundRequest(
    @NotBlank
    @Pattern(regexp = "^\\+[1-9][0-9]{1,14}$", message = "Phone must be E.164 (+ then digits)")
    String fromE164,

    @NotBlank @Size(max = 4096)
    String body
) {}
