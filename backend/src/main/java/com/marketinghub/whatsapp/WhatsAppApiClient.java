package com.marketinghub.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thin wrapper around Meta Cloud API for WhatsApp Business.
 *
 * Endpoint:  POST {base-url}/{phoneNumberId}/messages
 * Headers:   Authorization: Bearer <access token>
 * Body:      {"messaging_product":"whatsapp","to":"<E164>","type":"text","text":{"body":"..."}}
 * Response:  {"messaging_product":"whatsapp","contacts":[...],"messages":[{"id":"wamid.xxx"}]}
 *
 * Base URL is injectable (via property `whatsapp.api.base-url`) so tests can point at a stub.
 *
 * Mock mode: when `whatsapp.mock=true` (env WHATSAPP_MOCK=true), sendText short-circuits
 * and returns a fake wamid without ever touching the network. Useful for local
 * development before real Meta creds are available.
 */
@Component
public class WhatsAppApiClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppApiClient.class);
    private static final String MOCK_WAMID_PREFIX = "wamid.MOCK-";

    private final RestClient restClient;
    private final String baseUrl;
    private final boolean mockMode;

    public WhatsAppApiClient(
        @Value("${whatsapp.api.base-url:https://graph.facebook.com/v21.0}") String baseUrl,
        @Value("${whatsapp.mock:false}") boolean mockMode
    ) {
        this.baseUrl = baseUrl;
        this.mockMode = mockMode;
        this.restClient = RestClient.create();
        if (mockMode) {
            log.info("WhatsAppApiClient in MOCK mode — sendText() will not call Meta");
        }
    }

    public boolean isMockMode() {
        return mockMode;
    }

    /**
     * Send a plain text WhatsApp message. Returns Meta's whatsapp_message_id (wamid.*).
     * NOTE: Meta only delivers free-form text inside the 24-hour customer-service window
     * (i.e. to customers who messaged you in the last 24h). For cold/marketing sends use
     * {@link #sendTemplate}. Throws WhatsAppApiException on any non-2xx or shape problem.
     */
    public String sendText(String phoneNumberId, String accessToken, String toE164, String body) {
        if (mockMode) {
            return MOCK_WAMID_PREFIX + UUID.randomUUID();
        }
        Map<String, Object> reqBody = Map.of(
            "messaging_product", "whatsapp",
            "to", toE164,
            "type", "text",
            "text", Map.of("body", body)
        );
        return postForWamid(phoneNumberId, accessToken, reqBody);
    }

    /**
     * Send an approved WhatsApp <b>template</b> message — the only way to reach a customer
     * OUTSIDE the 24-hour window (the whole point of a marketing blast). {@code bodyParams}
     * fill the template's body variables {{1}}, {{2}}, ... in order. The template must already
     * be approved in Meta under {@code templateName} + {@code languageCode}, and the number of
     * params must match the template's variable count or Meta rejects the send.
     */
    public String sendTemplate(String phoneNumberId, String accessToken, String toE164,
                               String templateName, String languageCode, List<String> bodyParams) {
        if (mockMode) {
            return MOCK_WAMID_PREFIX + UUID.randomUUID();
        }
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateName);
        template.put("language", Map.of("code", languageCode));
        if (bodyParams != null && !bodyParams.isEmpty()) {
            List<Map<String, Object>> params = new ArrayList<>(bodyParams.size());
            for (String p : bodyParams) {
                params.add(Map.of("type", "text", "text", p == null ? "" : p));
            }
            template.put("components", List.of(Map.of("type", "body", "parameters", params)));
        }
        Map<String, Object> reqBody = Map.of(
            "messaging_product", "whatsapp",
            "to", toE164,
            "type", "template",
            "template", template
        );
        return postForWamid(phoneNumberId, accessToken, reqBody);
    }

    /** Shared POST to Meta + response parsing for both text and template sends. */
    private String postForWamid(String phoneNumberId, String accessToken, Map<String, Object> reqBody) {
        String url = baseUrl + "/" + phoneNumberId + "/messages";
        Map<?, ?> resp;
        try {
            resp = restClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(reqBody)
                .retrieve()
                .body(Map.class);
        } catch (RestClientResponseException e) {
            log.warn("Meta API error {} — body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new WhatsAppApiException(
                "Meta API " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (RestClientException e) {
            log.warn("Failed to call Meta API", e);
            throw new WhatsAppApiException("Failed to call Meta API: " + e.getMessage(), e);
        }

        if (resp == null) {
            throw new WhatsAppApiException("Meta returned empty response");
        }
        Object messagesObj = resp.get("messages");
        if (!(messagesObj instanceof List<?> list) || list.isEmpty()) {
            throw new WhatsAppApiException("Meta response missing messages[]: " + resp);
        }
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> m) || !(m.get("id") instanceof String id) || id.isBlank()) {
            throw new WhatsAppApiException("Meta response missing messages[0].id: " + resp);
        }
        return id;
    }
}
