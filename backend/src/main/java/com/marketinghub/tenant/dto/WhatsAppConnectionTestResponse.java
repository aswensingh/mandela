package com.marketinghub.tenant.dto;

/**
 * Result of "ping Meta with the stored creds" diagnostic.
 *
 * Success path: {@code ok=true} + the verified business name and display phone number from
 *               Meta's {@code GET /{phoneNumberId}?fields=...} response.
 * Failure path: {@code ok=false} + the HTTP status from Meta + Meta's own error message
 *               (or our network-level error) so the admin can see exactly what's wrong.
 */
public record WhatsAppConnectionTestResponse(
    boolean ok,
    String displayPhoneNumber,
    String verifiedName,
    String qualityRating,
    Integer status,
    String error
) {
    public static WhatsAppConnectionTestResponse success(
        String displayPhoneNumber, String verifiedName, String qualityRating
    ) {
        return new WhatsAppConnectionTestResponse(
            true, displayPhoneNumber, verifiedName, qualityRating, 200, null);
    }

    public static WhatsAppConnectionTestResponse failure(int status, String error) {
        return new WhatsAppConnectionTestResponse(false, null, null, null, status, error);
    }
}
