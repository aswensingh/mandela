package com.marketinghub.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketinghub.conversation.Conversation;
import com.marketinghub.conversation.ConversationRepository;
import com.marketinghub.conversation.ConversationStatus;
import com.marketinghub.customer.Customer;
import com.marketinghub.customer.CustomerRepository;
import com.marketinghub.customer.OptInStatus;
import com.marketinghub.message.Message;
import com.marketinghub.message.MessageDirection;
import com.marketinghub.message.MessageRepository;
import com.marketinghub.message.MessageStatus;
import com.marketinghub.message.SenderType;
import com.marketinghub.ai.AIAmqpConfig;
import com.marketinghub.ai.AIReplyMessage;
import com.marketinghub.tenant.Tenant;
import com.marketinghub.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Optional;

@Service
public class WhatsAppWebhookService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookService.class);
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final TenantRepository tenantRepository;
    private final CustomerRepository customerRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    private final String verifyToken;
    private final String appSecret;

    public WhatsAppWebhookService(
        TenantRepository tenantRepository,
        CustomerRepository customerRepository,
        ConversationRepository conversationRepository,
        MessageRepository messageRepository,
        WebhookEventRepository webhookEventRepository,
        ObjectMapper objectMapper,
        RabbitTemplate rabbitTemplate,
        @Value("${whatsapp.webhook.verify-token:}") String verifyToken,
        @Value("${whatsapp.webhook.app-secret:}") String appSecret
    ) {
        this.tenantRepository = tenantRepository;
        this.customerRepository = customerRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.verifyToken = verifyToken == null ? "" : verifyToken;
        this.appSecret = appSecret == null ? "" : appSecret;
    }

    /** Meta GET handshake — return the challenge iff mode=subscribe and token matches. */
    public boolean verifyHandshake(String mode, String token) {
        return "subscribe".equals(mode)
            && verifyToken != null
            && !verifyToken.isEmpty()
            && constantTimeEquals(verifyToken.getBytes(StandardCharsets.UTF_8),
                                  (token == null ? "" : token).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Validate Meta's X-Hub-Signature-256 header against the raw body using the app secret.
     * If no app-secret is configured we skip verification (dev convenience) with a warning.
     */
    public void verifySignature(byte[] rawBody, String headerSignature) {
        if (appSecret.isEmpty()) {
            log.warn("WHATSAPP_APP_SECRET unset — skipping HMAC verification (dev only)");
            return;
        }
        if (headerSignature == null || !headerSignature.startsWith(SIGNATURE_PREFIX)) {
            throw new WebhookSignatureException("Missing or malformed X-Hub-Signature-256 header");
        }
        String providedHex = headerSignature.substring(SIGNATURE_PREFIX.length()).trim();
        byte[] computed;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            computed = mac.doFinal(rawBody);
        } catch (GeneralSecurityException e) {
            throw new WebhookSignatureException("HMAC initialisation failed: " + e.getMessage());
        }
        byte[] provided;
        try {
            provided = hexToBytes(providedHex);
        } catch (IllegalArgumentException e) {
            throw new WebhookSignatureException("X-Hub-Signature-256 is not valid hex");
        }
        if (!MessageDigest.isEqual(computed, provided)) {
            throw new WebhookSignatureException("X-Hub-Signature-256 mismatch");
        }
    }

    /** Parse Meta's webhook payload and apply all message + status events. Idempotent. */
    @Transactional
    public WebhookProcessingResult processWebhook(byte[] rawBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.warn("Could not parse webhook JSON: {}", e.getMessage());
            return new WebhookProcessingResult(0, 0, 0);
        }
        int messagesAccepted = 0;
        int messagesDeduped = 0;
        int statusesApplied = 0;

        JsonNode entry = root.path("entry");
        if (!entry.isArray()) return new WebhookProcessingResult(0, 0, 0);
        for (JsonNode entryNode : entry) {
            JsonNode changes = entryNode.path("changes");
            if (!changes.isArray()) continue;
            for (JsonNode change : changes) {
                JsonNode value = change.path("value");
                String phoneNumberId = value.path("metadata").path("phone_number_id").asText(null);
                Optional<Tenant> tenantOpt = phoneNumberId == null
                    ? Optional.empty()
                    : tenantRepository.findByWhatsappPhoneNumberId(phoneNumberId);
                if (tenantOpt.isEmpty()) {
                    log.warn("Webhook for unknown phone_number_id={} — skipping", phoneNumberId);
                    continue;
                }
                Tenant tenant = tenantOpt.get();

                JsonNode messages = value.path("messages");
                if (messages.isArray()) {
                    for (JsonNode m : messages) {
                        InboundOutcome o = handleInbound(tenant, m);
                        if (o == InboundOutcome.ACCEPTED) messagesAccepted++;
                        else if (o == InboundOutcome.DEDUP) messagesDeduped++;
                    }
                }

                JsonNode statuses = value.path("statuses");
                if (statuses.isArray()) {
                    for (JsonNode s : statuses) {
                        if (handleStatus(s)) statusesApplied++;
                    }
                }
            }
        }
        return new WebhookProcessingResult(messagesAccepted, messagesDeduped, statusesApplied);
    }

    private InboundOutcome handleInbound(Tenant tenant, JsonNode msg) {
        String wamid = msg.path("id").asText(null);
        if (wamid == null || wamid.isBlank()) {
            log.warn("Inbound message missing id — skipping");
            return InboundOutcome.SKIPPED;
        }

        // Race-safe dedup claim via native ON CONFLICT DO NOTHING. Using JPA save() + flush()
        // throws DataIntegrityViolationException on duplicate, which marks the surrounding
        // @Transactional as rollback-only and makes the eventual commit blow up with a 500 —
        // even though we catch the exception. The native INSERT just returns 0 instead.
        if (webhookEventRepository.tryClaim(wamid) == 0) {
            log.debug("Dedup: webhook already processed for wamid={}", wamid);
            return InboundOutcome.DEDUP;
        }

        String fromRaw = msg.path("from").asText(null);
        if (fromRaw == null || fromRaw.isBlank()) {
            log.warn("Inbound message {} missing 'from' — saving event but no message row", wamid);
            return InboundOutcome.SKIPPED;
        }
        String phoneE164 = fromRaw.startsWith("+") ? fromRaw : "+" + fromRaw;

        Customer customer = customerRepository
            .findByTenantIdAndPhoneE164(tenant.getId(), phoneE164)
            .orElseGet(() -> {
                Customer c = new Customer();
                c.setTenantId(tenant.getId());
                c.setPhoneE164(phoneE164);
                // Best-effort enrichment from Meta's contacts[] entry.
                c.setFullName(null);
                c.setTags(new ArrayList<>());
                c.setOptInStatus(OptInStatus.UNKNOWN);
                c.setCustomAttributes(new HashMap<>());
                return customerRepository.save(c);
            });

        Conversation convo = conversationRepository
            .findByTenantIdAndCustomerId(tenant.getId(), customer.getId())
            .orElseGet(() -> {
                Conversation c = new Conversation();
                c.setTenantId(tenant.getId());
                c.setCustomerId(customer.getId());
                c.setStatus(ConversationStatus.BOT_ACTIVE);
                return conversationRepository.save(c);
            });

        String body = msg.path("text").path("body").asText("");
        Message message = new Message();
        message.setTenantId(tenant.getId());
        message.setCustomerId(customer.getId());
        message.setDirection(MessageDirection.IN);
        message.setSenderType(SenderType.CUSTOMER);
        message.setBody(body);
        message.setWhatsappMessageId(wamid);
        message.setStatus(MessageStatus.DELIVERED); // inbound is delivered to us by definition
        messageRepository.save(message);

        convo.setLastMessageAt(Instant.now());

        // If the conversation is bot-driven, enqueue an AI reply job. The worker reads
        // from a separate DB connection, so publish only after the surrounding webhook
        // tx commits — otherwise the worker can race past our as-yet-uncommitted rows.
        if (convo.getStatus() == ConversationStatus.BOT_ACTIVE) {
            AIReplyMessage envelope = new AIReplyMessage(tenant.getId(), convo.getId(), customer.getId());
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() {
                        rabbitTemplate.convertAndSend(
                            AIAmqpConfig.AI_REPLY_EXCHANGE,
                            AIAmqpConfig.AI_REPLY_ROUTING_KEY,
                            envelope);
                    }
                });
            } else {
                rabbitTemplate.convertAndSend(
                    AIAmqpConfig.AI_REPLY_EXCHANGE,
                    AIAmqpConfig.AI_REPLY_ROUTING_KEY,
                    envelope);
            }
        }

        return InboundOutcome.ACCEPTED;
    }

    private boolean handleStatus(JsonNode s) {
        String wamid = s.path("id").asText(null);
        String status = s.path("status").asText(null);
        if (wamid == null || status == null) return false;
        Optional<Message> existing = messageRepository.findByWhatsappMessageId(wamid);
        if (existing.isEmpty()) {
            log.debug("Status update for unknown wamid={} — ignoring", wamid);
            return false;
        }
        MessageStatus next = mapMetaStatus(status);
        if (next == null) return false;
        Message m = existing.get();
        // Don't downgrade — Meta sometimes sends out-of-order updates.
        if (statusRank(next) <= statusRank(m.getStatus())) return false;
        m.setStatus(next);
        return true;
    }

    private static MessageStatus mapMetaStatus(String metaStatus) {
        return switch (metaStatus.toLowerCase()) {
            case "sent" -> MessageStatus.SENT;
            case "delivered" -> MessageStatus.DELIVERED;
            case "read" -> MessageStatus.READ;
            case "failed" -> MessageStatus.FAILED;
            default -> null;
        };
    }

    private static int statusRank(MessageStatus s) {
        return switch (s) {
            case QUEUED -> 0;
            case SENT -> 1;
            case DELIVERED -> 2;
            case READ -> 3;
            case FAILED -> 4;
        };
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        if ((len & 1) != 0) throw new IllegalArgumentException("Odd-length hex");
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("Non-hex char");
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    public enum InboundOutcome { ACCEPTED, DEDUP, SKIPPED }
    public record WebhookProcessingResult(int messagesAccepted, int messagesDeduped, int statusesApplied) {}
}
