package com.marketinghub.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final WhatsAppWebhookService service;

    public WhatsAppWebhookController(WhatsAppWebhookService service) {
        this.service = service;
    }

    /** Meta calls this once when verifying the webhook URL. */
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> handshake(
        @RequestParam(name = "hub.mode", required = false) String mode,
        @RequestParam(name = "hub.verify_token", required = false) String token,
        @RequestParam(name = "hub.challenge", required = false) String challenge
    ) {
        if (service.verifyHandshake(mode, token)) {
            return ResponseEntity.ok(challenge == null ? "" : challenge);
        }
        return ResponseEntity.status(403).body("forbidden");
    }

    /**
     * Meta delivers events here. The body MUST be read as raw bytes — we recompute HMAC
     * over exactly the bytes Meta sent.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> receive(
        @RequestBody byte[] rawBody,
        @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature
    ) {
        try {
            service.verifySignature(rawBody, signature);
        } catch (WebhookSignatureException e) {
            log.warn("Webhook signature rejected: {}", e.getMessage());
            return ResponseEntity.status(401).body("invalid signature");
        }
        WhatsAppWebhookService.WebhookProcessingResult result = service.processWebhook(rawBody);
        log.info("Webhook processed: accepted={} deduped={} skipped={} statuses={} unknownPhoneChanges={}",
            result.messagesAccepted(),
            result.messagesDeduped(),
            result.messagesSkipped(),
            result.statusesApplied(),
            result.changesIgnoredUnknownPhoneNumber());
        return ResponseEntity.ok("");
    }
}
