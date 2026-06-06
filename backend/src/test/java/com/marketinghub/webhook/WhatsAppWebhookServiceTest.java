package com.marketinghub.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketinghub.campaign.CampaignRecipientRepository;
import com.marketinghub.conversation.Conversation;
import com.marketinghub.conversation.ConversationRepository;
import com.marketinghub.customer.Customer;
import com.marketinghub.customer.CustomerRepository;
import com.marketinghub.message.Message;
import com.marketinghub.message.MessageDirection;
import com.marketinghub.message.MessageRepository;
import com.marketinghub.message.MessageStatus;
import com.marketinghub.message.SenderType;
import com.marketinghub.tenant.Tenant;
import com.marketinghub.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private WebhookEventRepository webhookEventRepository;
    @Mock private CampaignRecipientRepository recipientRepository;
    @Mock private RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String VERIFY_TOKEN = "test-verify-token-xyz";
    private static final String APP_SECRET = "test-app-secret-12345";
    private static final String PHONE_ID = "PHN-987654321";
    private static final UUID TENANT_ID = UUID.randomUUID();

    private WhatsAppWebhookService service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppWebhookService(
            tenantRepository, customerRepository, conversationRepository,
            messageRepository, webhookEventRepository, recipientRepository,
            objectMapper, rabbitTemplate, VERIFY_TOKEN, APP_SECRET);
    }

    @Test
    void handshake_matches_onCorrectTokenAndMode() {
        assertThat(service.verifyHandshake("subscribe", VERIFY_TOKEN)).isTrue();
    }

    @Test
    void handshake_rejects_onWrongToken() {
        assertThat(service.verifyHandshake("subscribe", "nope")).isFalse();
    }

    @Test
    void handshake_rejects_onWrongMode() {
        assertThat(service.verifyHandshake("unsubscribe", VERIFY_TOKEN)).isFalse();
    }

    @Test
    void signature_verifies_validHmac() throws Exception {
        byte[] body = "{\"foo\":1}".getBytes(StandardCharsets.UTF_8);
        String sig = "sha256=" + hmacHex(APP_SECRET, body);
        service.verifySignature(body, sig);  // does not throw
    }

    @Test
    void signature_rejects_wrongHmac() {
        byte[] body = "{\"foo\":1}".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.verifySignature(body, "sha256=deadbeef"))
            .isInstanceOf(WebhookSignatureException.class);
    }

    @Test
    void signature_rejects_missingHeader() {
        assertThatThrownBy(() -> service.verifySignature("{}".getBytes(), null))
            .isInstanceOf(WebhookSignatureException.class)
            .hasMessageContaining("Missing");
    }

    @Test
    void signature_skipped_whenAppSecretBlank() {
        WhatsAppWebhookService noSecret = new WhatsAppWebhookService(
            tenantRepository, customerRepository, conversationRepository,
            messageRepository, webhookEventRepository, recipientRepository,
            objectMapper, rabbitTemplate, VERIFY_TOKEN, "");
        noSecret.verifySignature("{}".getBytes(), "sha256=anything"); // no throw
    }

    @Test
    void inboundMessage_createsCustomerConversationMessageAndDedupEvent() {
        Tenant tenant = stubTenant();
        when(tenantRepository.findByWhatsappPhoneNumberId(PHONE_ID)).thenReturn(Optional.of(tenant));
        when(customerRepository.findByTenantIdAndPhoneE164(TENANT_ID, "+16315551234"))
            .thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(conversationRepository.findByTenantIdAndCustomerId(any(), any())).thenReturn(Optional.empty());
        AtomicReference<Conversation> savedConvo = new AtomicReference<>();
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            savedConvo.set(c);
            return c;
        });
        AtomicReference<Message> savedMessage = new AtomicReference<>();
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            savedMessage.set(m);
            return m;
        });
        // Dedup claim succeeds (1 = newly inserted).
        when(webhookEventRepository.tryClaim(anyString())).thenReturn(1);

        String json = inboundPayload(PHONE_ID, "wamid.HBgM_TEST_001", "16315551234", "Hello bot");
        var result = service.processWebhook(json.getBytes(StandardCharsets.UTF_8));

        assertThat(result.messagesAccepted()).isEqualTo(1);
        assertThat(result.messagesDeduped()).isZero();
        assertThat(savedMessage.get().getDirection()).isEqualTo(MessageDirection.IN);
        assertThat(savedMessage.get().getSenderType()).isEqualTo(SenderType.CUSTOMER);
        assertThat(savedMessage.get().getWhatsappMessageId()).isEqualTo("wamid.HBgM_TEST_001");
        assertThat(savedMessage.get().getBody()).isEqualTo("Hello bot");
        assertThat(savedMessage.get().getStatus()).isEqualTo(MessageStatus.DELIVERED);
        assertThat(savedConvo.get().getTenantId()).isEqualTo(TENANT_ID);
        assertThat(savedConvo.get().getLastMessageAt()).isNotNull();
    }

    @Test
    void inboundMessage_isDedupedOnReplay() {
        Tenant tenant = stubTenant();
        when(tenantRepository.findByWhatsappPhoneNumberId(PHONE_ID)).thenReturn(Optional.of(tenant));
        // tryClaim returns 0 means the row was already there → dedup.
        when(webhookEventRepository.tryClaim("wamid.replay-1")).thenReturn(0);

        String json = inboundPayload(PHONE_ID, "wamid.replay-1", "16315551234", "second time");
        var result = service.processWebhook(json.getBytes(StandardCharsets.UTF_8));

        assertThat(result.messagesAccepted()).isZero();
        assertThat(result.messagesDeduped()).isEqualTo(1);
        verify(messageRepository, never()).save(any());
        verify(customerRepository, never()).save(any());
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void inbound_forUnknownPhoneNumberId_isSilentlyIgnored() {
        when(tenantRepository.findByWhatsappPhoneNumberId("unknown-phone")).thenReturn(Optional.empty());
        String json = inboundPayload("unknown-phone", "wamid.x", "16315551234", "hi");
        var result = service.processWebhook(json.getBytes(StandardCharsets.UTF_8));
        assertThat(result.messagesAccepted()).isZero();
        verify(messageRepository, never()).save(any());
    }

    @Test
    void statusUpdate_advancesExistingMessage_butDoesNotDowngrade() {
        Tenant tenant = stubTenant();
        when(tenantRepository.findByWhatsappPhoneNumberId(PHONE_ID)).thenReturn(Optional.of(tenant));
        Message existing = new Message();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(TENANT_ID);
        existing.setStatus(MessageStatus.SENT);
        existing.setWhatsappMessageId("wamid.HBgM_OUT_001");
        when(messageRepository.findByWhatsappMessageId("wamid.HBgM_OUT_001"))
            .thenReturn(Optional.of(existing));

        String json = statusPayload(PHONE_ID, "wamid.HBgM_OUT_001", "delivered");
        var result = service.processWebhook(json.getBytes(StandardCharsets.UTF_8));
        assertThat(result.statusesApplied()).isEqualTo(1);
        assertThat(existing.getStatus()).isEqualTo(MessageStatus.DELIVERED);

        // Now a stale "sent" arrives — must NOT downgrade.
        String stale = statusPayload(PHONE_ID, "wamid.HBgM_OUT_001", "sent");
        var stale2 = service.processWebhook(stale.getBytes(StandardCharsets.UTF_8));
        assertThat(stale2.statusesApplied()).isZero();
        assertThat(existing.getStatus()).isEqualTo(MessageStatus.DELIVERED);
    }

    @Test
    void statusUpdate_forUnknownWamid_isIgnored() {
        Tenant tenant = stubTenant();
        when(tenantRepository.findByWhatsappPhoneNumberId(PHONE_ID)).thenReturn(Optional.of(tenant));
        when(messageRepository.findByWhatsappMessageId(anyString())).thenReturn(Optional.empty());
        String json = statusPayload(PHONE_ID, "wamid.unknown", "delivered");
        var result = service.processWebhook(json.getBytes(StandardCharsets.UTF_8));
        assertThat(result.statusesApplied()).isZero();
    }

    @Test
    void inboundMessage_normalisesPhone_withLeadingPlus() {
        Tenant tenant = stubTenant();
        when(tenantRepository.findByWhatsappPhoneNumberId(PHONE_ID)).thenReturn(Optional.of(tenant));
        // Meta sends "from" without a leading '+' — we should add it for our citext column.
        when(customerRepository.findByTenantIdAndPhoneE164(TENANT_ID, "+16315551234"))
            .thenReturn(Optional.empty());
        ArgumentCaptor<Customer> cap = ArgumentCaptor.forClass(Customer.class);
        when(customerRepository.save(cap.capture())).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(conversationRepository.findByTenantIdAndCustomerId(any(), any())).thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        when(webhookEventRepository.tryClaim(anyString())).thenReturn(1);

        service.processWebhook(
            inboundPayload(PHONE_ID, "wamid.norm-1", "16315551234", "hi").getBytes(StandardCharsets.UTF_8));
        assertThat(cap.getValue().getPhoneE164()).isEqualTo("+16315551234");
    }

    // ---------- helpers ----------

    private Tenant stubTenant() {
        Tenant t = new Tenant();
        t.setId(TENANT_ID);
        t.setName("Acme");
        t.setWhatsappPhoneNumberId(PHONE_ID);
        return t;
    }

    private static String inboundPayload(String phoneNumberId, String wamid, String fromWaId, String body) {
        return ""
            + "{\"object\":\"whatsapp_business_account\","
            + "\"entry\":[{\"id\":\"WABA\",\"changes\":[{"
            + "\"value\":{"
            + "\"messaging_product\":\"whatsapp\","
            + "\"metadata\":{\"phone_number_id\":\"" + phoneNumberId + "\"},"
            + "\"contacts\":[{\"wa_id\":\"" + fromWaId + "\"}],"
            + "\"messages\":[{"
            + "\"from\":\"" + fromWaId + "\","
            + "\"id\":\"" + wamid + "\","
            + "\"timestamp\":\"1700000000\","
            + "\"type\":\"text\","
            + "\"text\":{\"body\":\"" + body + "\"}"
            + "}]},"
            + "\"field\":\"messages\"}]}]}";
    }

    private static String statusPayload(String phoneNumberId, String wamid, String status) {
        return ""
            + "{\"object\":\"whatsapp_business_account\","
            + "\"entry\":[{\"id\":\"WABA\",\"changes\":[{"
            + "\"value\":{"
            + "\"messaging_product\":\"whatsapp\","
            + "\"metadata\":{\"phone_number_id\":\"" + phoneNumberId + "\"},"
            + "\"statuses\":[{"
            + "\"id\":\"" + wamid + "\","
            + "\"status\":\"" + status + "\","
            + "\"timestamp\":\"1700000000\""
            + "}]},"
            + "\"field\":\"messages\"}]}]}";
    }

    private static String hmacHex(String secret, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }
}
