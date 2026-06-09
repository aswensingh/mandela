package com.marketinghub.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketinghub.common.TestToolsGuard;
import com.marketinghub.common.crypto.EncryptionService;
import com.marketinghub.tenant.dto.SimulateInboundRequest;
import com.marketinghub.tenant.dto.SimulateInboundResponse;
import com.marketinghub.tenant.dto.UpdateWhatsAppConfigRequest;
import com.marketinghub.tenant.dto.WhatsAppConfigStatusDto;
import com.marketinghub.tenant.dto.WhatsAppConnectionTestResponse;
import com.marketinghub.webhook.WhatsAppWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

@Service
public class WhatsAppConfigService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppConfigService.class);

    private final TenantRepository tenantRepository;
    private final EncryptionService encryptionService;
    private final WhatsAppWebhookService webhookService;
    private final TestToolsGuard testToolsGuard;
    private final String metaBaseUrl;
    private final boolean whatsAppMock;
    private final RestClient restClient;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public WhatsAppConfigService(
        TenantRepository tenantRepository,
        EncryptionService encryptionService,
        WhatsAppWebhookService webhookService,
        TestToolsGuard testToolsGuard,
        @Value("${whatsapp.api.base-url:https://graph.facebook.com/v21.0}") String metaBaseUrl,
        @Value("${whatsapp.mock:false}") boolean whatsAppMock
    ) {
        this.tenantRepository = tenantRepository;
        this.encryptionService = encryptionService;
        this.webhookService = webhookService;
        this.testToolsGuard = testToolsGuard;
        this.metaBaseUrl = metaBaseUrl;
        this.whatsAppMock = whatsAppMock;
        this.restClient = RestClient.create();
    }

    @Transactional(readOnly = true)
    public WhatsAppConfigStatusDto getMyConfig() {
        Tenant tenant = currentTenant();
        boolean configured = tenant.getWhatsappPhoneNumberId() != null
            && tenant.getWhatsappAccessTokenEncrypted() != null;
        String lastFour = null;
        if (configured) {
            String plaintext = encryptionService.decrypt(tenant.getWhatsappAccessTokenEncrypted());
            int n = plaintext.length();
            lastFour = n <= 4 ? plaintext : plaintext.substring(n - 4);
        }
        return new WhatsAppConfigStatusDto(configured, tenant.getWhatsappPhoneNumberId(), lastFour,
            tenant.getWhatsappBusinessAccountId(), testToolsGuard.enabled());
    }

    @Transactional
    public WhatsAppConfigStatusDto updateMyConfig(UpdateWhatsAppConfigRequest request) {
        Tenant tenant = currentTenant();
        tenant.setWhatsappPhoneNumberId(request.phoneNumberId());
        tenant.setWhatsappAccessTokenEncrypted(encryptionService.encrypt(request.accessToken()));
        // WABA ID is optional — normalise blank to null so "not set" is unambiguous.
        String waba = request.businessAccountId();
        tenant.setWhatsappBusinessAccountId(waba == null || waba.isBlank() ? null : waba.trim());
        int n = request.accessToken().length();
        String lastFour = n <= 4 ? request.accessToken() : request.accessToken().substring(n - 4);
        return new WhatsAppConfigStatusDto(true, tenant.getWhatsappPhoneNumberId(), lastFour,
            tenant.getWhatsappBusinessAccountId(), testToolsGuard.enabled());
    }

    /**
     * Diagnostic: ping Meta's {@code GET /{phoneNumberId}?fields=display_phone_number,verified_name,quality_rating}
     * with the tenant's currently-stored credentials and report back what we got. Useful
     * for spotting wrong tokens, expired tokens, missing permissions, or the phone-number-id
     * not matching the token's scope — without waiting for a real send to fail.
     *
     * When WHATSAPP_MOCK=true we don't actually call Meta — we just confirm the creds are
     * present and return a synthetic "ok in mock mode" response.
     */
    @Transactional(readOnly = true)
    public WhatsAppConnectionTestResponse testConnection() {
        Tenant tenant = currentTenant();
        String phoneNumberId = tenant.getWhatsappPhoneNumberId();
        byte[] encToken = tenant.getWhatsappAccessTokenEncrypted();
        if (phoneNumberId == null || encToken == null) {
            return WhatsAppConnectionTestResponse.failure(0,
                "No WhatsApp credentials configured. Save your Phone Number ID and Access Token first.");
        }
        if (whatsAppMock) {
            return new WhatsAppConnectionTestResponse(
                true,
                "+1 555 0100 (mock)",
                "Mock Business",
                "GREEN",
                200,
                "WHATSAPP_MOCK=true — credentials look present but no real Meta call was made.");
        }

        String token = encryptionService.decrypt(encToken);
        String url = metaBaseUrl + "/" + phoneNumberId
            + "?fields=display_phone_number,verified_name,quality_rating";
        try {
            // Read the body as String + parse with Jackson ourselves. Meta's Graph API
            // sometimes responds with Content-Type: text/javascript;charset=UTF-8 (a relic
            // of its old JSONP-friendly days) and Spring's auto-converters refuse to
            // deserialise that to Map. Reading as String sidesteps the converter selection
            // entirely. We still set Accept: application/json so Meta picks the cleaner
            // path when it can.
            String body = restClient.get()
                .uri(url)
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON, MediaType.valueOf("text/javascript"))
                .retrieve()
                .body(String.class);
            if (body == null || body.isBlank()) {
                return WhatsAppConnectionTestResponse.failure(200, "Meta returned empty response");
            }
            JsonNode root;
            try {
                root = jsonMapper.readTree(body);
            } catch (Exception parseErr) {
                String preview = body.length() > 200 ? body.substring(0, 200) + "…" : body;
                return WhatsAppConnectionTestResponse.failure(200,
                    "Meta returned non-JSON body: " + preview);
            }
            // Meta's error envelope: {"error":{"message":"...","code":190,...}}
            JsonNode err = root.get("error");
            if (err != null) {
                int code = err.path("code").asInt(0);
                String msg = err.path("message").asText("Unknown Meta error");
                return WhatsAppConnectionTestResponse.failure(code, msg);
            }
            String displayPhone = root.path("display_phone_number").asText(null);
            String verifiedName = root.path("verified_name").asText(null);
            String quality = root.path("quality_rating").asText(null);
            log.info("WhatsApp connection test OK for tenant {} — verified_name='{}', display={}",
                tenant.getId(), verifiedName, displayPhone);
            return WhatsAppConnectionTestResponse.success(displayPhone, verifiedName, quality);
        } catch (RestClientResponseException e) {
            log.warn("WhatsApp connection test failed for tenant {}: {} {}",
                tenant.getId(), e.getStatusCode(), e.getResponseBodyAsString());
            return WhatsAppConnectionTestResponse.failure(
                e.getStatusCode().value(),
                summariseMetaError(e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.warn("WhatsApp connection test threw for tenant {}: {}", tenant.getId(), e.getMessage());
            return WhatsAppConnectionTestResponse.failure(0,
                "Could not reach Meta: " + e.getMessage());
        }
    }

    /**
     * QA / demo helper: pretends a customer just sent us a WhatsApp message.
     *
     * Builds a Meta-shaped webhook payload using the tenant's own
     * {@code phone_number_id} so it routes back to the same tenant, then hands
     * the bytes directly to {@link WhatsAppWebhookService#processWebhook} —
     * skipping the HTTP layer + HMAC check (we trust ourselves). From there
     * the normal pipeline takes over: conversation created/updated, AI worker
     * fires, and (if Meta lets us) a reply goes back to the customer's number.
     *
     * Useful because Meta's free test number has unreliable webhook delivery
     * for real inbound traffic in development mode — this gives tenant admins
     * a guaranteed-working test loop without Meta in the path.
     */
    @Transactional
    public SimulateInboundResponse simulateInbound(SimulateInboundRequest request) {
        Tenant tenant = currentTenant();
        String phoneNumberId = tenant.getWhatsappPhoneNumberId();
        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            throw new IllegalStateException(
                "Tenant has no WhatsApp Phone Number ID configured");
        }
        // Strip the leading '+' for the wa_id / from fields (Meta uses digits only).
        String waId = request.fromE164().startsWith("+")
            ? request.fromE164().substring(1)
            : request.fromE164();
        String wamid = "wamid.SIM-" + java.util.UUID.randomUUID();

        java.util.Map<String, Object> payload = java.util.Map.of(
            "object", "whatsapp_business_account",
            "entry", java.util.List.of(java.util.Map.of(
                "id", tenant.getId().toString(),
                "changes", java.util.List.of(java.util.Map.of(
                    "field", "messages",
                    "value", java.util.Map.of(
                        "messaging_product", "whatsapp",
                        "metadata", java.util.Map.of(
                            "display_phone_number", "+1 555-201-6223",
                            "phone_number_id", phoneNumberId
                        ),
                        "contacts", java.util.List.of(java.util.Map.of(
                            "profile", java.util.Map.of("name", "Simulated " + waId),
                            "wa_id", waId
                        )),
                        "messages", java.util.List.of(java.util.Map.of(
                            "from", waId,
                            "id", wamid,
                            "timestamp", String.valueOf(java.time.Instant.now().getEpochSecond()),
                            "type", "text",
                            "text", java.util.Map.of("body", request.body())
                        ))
                    )
                ))
            ))
        );

        byte[] body;
        try {
            body = jsonMapper.writeValueAsBytes(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise simulate payload", e);
        }

        com.marketinghub.webhook.WhatsAppWebhookService.WebhookProcessingResult result =
            webhookService.processWebhook(body);
        log.info("Simulate inbound for tenant {} → accepted={}", tenant.getId(), result.messagesAccepted());
        return new SimulateInboundResponse(result.messagesAccepted(), wamid);
    }

    /**
     * Meta's error responses look like {@code {"error":{"message":"...","type":"OAuthException",...}}}.
     * Pull the message out so we can show something readable in the UI instead of the raw blob.
     */
    private static String summariseMetaError(String body) {
        if (body == null || body.isBlank()) return "Meta returned an error with no body";
        // Crude but reliable: find "message":"..."
        int idx = body.indexOf("\"message\":\"");
        if (idx < 0) return body.length() > 400 ? body.substring(0, 400) + "…" : body;
        int start = idx + "\"message\":\"".length();
        int end = body.indexOf("\"", start);
        if (end < 0) return body.substring(start);
        return body.substring(start, end);
    }

    private Tenant currentTenant() {
        UUID tenantId = com.marketinghub.tenant.TenantContext.getTenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("No tenant context");
        }
        return tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId));
    }
}
