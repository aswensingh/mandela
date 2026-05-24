package com.marketinghub.ai;

import com.marketinghub.common.crypto.EncryptionService;
import com.marketinghub.conversation.Conversation;
import com.marketinghub.conversation.ConversationRepository;
import com.marketinghub.conversation.ConversationStatus;
import com.marketinghub.customer.Customer;
import com.marketinghub.customer.CustomerRepository;
import com.marketinghub.message.Message;
import com.marketinghub.message.MessageDirection;
import com.marketinghub.message.MessageRepository;
import com.marketinghub.message.MessageStatus;
import com.marketinghub.message.SenderType;
import com.marketinghub.tenant.Tenant;
import com.marketinghub.tenant.TenantRepository;
import com.marketinghub.whatsapp.WhatsAppApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIReplyWorkerTest {

    @Mock private AIChatClient chatClient;
    @Mock private TenantRepository tenantRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private WhatsAppApiClient apiClient;
    @Mock private EncryptionService encryptionService;
    @Mock private VectorStore vectorStore;

    private final PlatformTransactionManager noopTm = new PlatformTransactionManager() {
        @Override public TransactionStatus getTransaction(TransactionDefinition def) { return new SimpleTransactionStatus(); }
        @Override public void commit(TransactionStatus s) {}
        @Override public void rollback(TransactionStatus s) {}
    };

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID CONVO_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    private AIReplyWorker worker;

    @BeforeEach
    void setUp() {
        worker = new AIReplyWorker(
            chatClient,
            tenantRepository,
            conversationRepository,
            customerRepository,
            messageRepository,
            encryptionService,
            apiClient,
            vectorStore,
            noopTm,
            10,
            0.5,
            "default system prompt",
            4,
            0.0
        );
        // VectorStore returns no chunks by default; tests that exercise RAG opt-in.
        // Lenient because not every test triggers retrieval (e.g., not-BOT_ACTIVE short-circuit).
        lenient().when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
    }

    @Test
    void happyReply_persistsBotMessage_andSendsWA() {
        Conversation convo = botConvo();
        when(conversationRepository.findById(CONVO_ID)).thenReturn(Optional.of(convo));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(stubTenant(null, null)));
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(stubCustomer()));
        when(messageRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAsc(
            eq(TENANT_ID), eq(CUSTOMER_ID), any(Pageable.class)))
            .thenReturn(stubHistory("Hello there"));
        when(chatClient.generateReply(any(AIChatRequest.class)))
            .thenReturn(new BotReply("Hi! How can I help?", 0.9, false));
        when(apiClient.isMockMode()).thenReturn(true);
        when(apiClient.sendText(any(), any(), anyString(), eq("Hi! How can I help?")))
            .thenReturn("wamid.MOCK-abc");

        AtomicReference<Message> saved = new AtomicReference<>();
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            saved.set(m);
            return m;
        });

        worker.onMessage(new AIReplyMessage(TENANT_ID, CONVO_ID, CUSTOMER_ID));

        assertThat(saved.get()).isNotNull();
        assertThat(saved.get().getDirection()).isEqualTo(MessageDirection.OUT);
        assertThat(saved.get().getSenderType()).isEqualTo(SenderType.BOT);
        assertThat(saved.get().getBody()).isEqualTo("Hi! How can I help?");
        assertThat(saved.get().getStatus()).isEqualTo(MessageStatus.SENT);
        assertThat(saved.get().getWhatsappMessageId()).isEqualTo("wamid.MOCK-abc");
        // Conversation stays BOT_ACTIVE
        assertThat(convo.getStatus()).isEqualTo(ConversationStatus.BOT_ACTIVE);
    }

    @Test
    void handoffRequested_flipsToHumanActive_doesNotSendOrPersistMessage() {
        Conversation convo = botConvo();
        when(conversationRepository.findById(CONVO_ID)).thenReturn(Optional.of(convo));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(stubTenant(null, null)));
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(stubCustomer()));
        when(messageRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAsc(
            eq(TENANT_ID), eq(CUSTOMER_ID), any(Pageable.class)))
            .thenReturn(stubHistory("I want to talk to a human"));
        when(chatClient.generateReply(any(AIChatRequest.class)))
            .thenReturn(new BotReply("Connecting you to a human.", 0.95, true));

        worker.onMessage(new AIReplyMessage(TENANT_ID, CONVO_ID, CUSTOMER_ID));

        assertThat(convo.getStatus()).isEqualTo(ConversationStatus.HUMAN_ACTIVE);
        verify(apiClient, never()).sendText(any(), any(), any(), any());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void lowConfidence_belowTenantThreshold_alsoFlipsToHumanActive() {
        // Custom threshold = 0.8; bot returns 0.6 → handoff even though request_handoff=false.
        Conversation convo = botConvo();
        when(conversationRepository.findById(CONVO_ID)).thenReturn(Optional.of(convo));
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("handoff_confidence_threshold", 0.8);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(stubTenant(null, cfg)));
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(stubCustomer()));
        when(messageRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAsc(
            eq(TENANT_ID), eq(CUSTOMER_ID), any(Pageable.class)))
            .thenReturn(stubHistory("ambiguous question"));
        when(chatClient.generateReply(any(AIChatRequest.class)))
            .thenReturn(new BotReply("I think it's…", 0.6, false));

        worker.onMessage(new AIReplyMessage(TENANT_ID, CONVO_ID, CUSTOMER_ID));

        assertThat(convo.getStatus()).isEqualTo(ConversationStatus.HUMAN_ACTIVE);
        verify(apiClient, never()).sendText(any(), any(), any(), any());
    }

    @Test
    void notBotActive_skips() {
        Conversation convo = botConvo();
        convo.setStatus(ConversationStatus.HUMAN_ACTIVE);
        when(conversationRepository.findById(CONVO_ID)).thenReturn(Optional.of(convo));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(stubTenant(null, null)));
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(stubCustomer()));
        when(messageRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAsc(
            eq(TENANT_ID), eq(CUSTOMER_ID), any(Pageable.class)))
            .thenReturn(stubHistory("ignored"));

        worker.onMessage(new AIReplyMessage(TENANT_ID, CONVO_ID, CUSTOMER_ID));

        // Should not have called the LLM, the WA client, or saved any message.
        verify(chatClient, never()).generateReply(any());
        verify(apiClient, never()).sendText(any(), any(), any(), any());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void tenantSystemPrompt_isUsedWhenPresent() {
        Conversation convo = botConvo();
        when(conversationRepository.findById(CONVO_ID)).thenReturn(Optional.of(convo));
        when(tenantRepository.findById(TENANT_ID))
            .thenReturn(Optional.of(stubTenant("you are a dental concierge", null)));
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(stubCustomer()));
        when(messageRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAsc(
            eq(TENANT_ID), eq(CUSTOMER_ID), any(Pageable.class)))
            .thenReturn(stubHistory("question"));
        when(apiClient.isMockMode()).thenReturn(true);
        when(apiClient.sendText(any(), any(), anyString(), anyString()))
            .thenReturn("wamid.MOCK-1");
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        AtomicReference<AIChatRequest> captured = new AtomicReference<>();
        when(chatClient.generateReply(any(AIChatRequest.class))).thenAnswer(inv -> {
            captured.set(inv.getArgument(0));
            return new BotReply("ok", 0.9, false);
        });

        worker.onMessage(new AIReplyMessage(TENANT_ID, CONVO_ID, CUSTOMER_ID));

        assertThat(captured.get().systemPrompt()).isEqualTo("you are a dental concierge");
    }

    @Test
    void retrievedChunks_arePassedToChatClient() {
        Conversation convo = botConvo();
        when(conversationRepository.findById(CONVO_ID)).thenReturn(Optional.of(convo));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(stubTenant(null, null)));
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(stubCustomer()));
        when(messageRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAsc(
            eq(TENANT_ID), eq(CUSTOMER_ID), any(Pageable.class)))
            .thenReturn(stubHistory("how much for whitening?"));

        Map<String, Object> md = new HashMap<>();
        md.put("tenant_id", TENANT_ID.toString());
        md.put("document_title", "FAQ");
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
            .thenReturn(List.of(new Document("Our clinic offers teeth whitening at $150.", md)));

        when(apiClient.isMockMode()).thenReturn(true);
        when(apiClient.sendText(any(), any(), anyString(), anyString()))
            .thenReturn("wamid.MOCK-1");
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        AtomicReference<AIChatRequest> captured = new AtomicReference<>();
        when(chatClient.generateReply(any(AIChatRequest.class))).thenAnswer(inv -> {
            captured.set(inv.getArgument(0));
            return new BotReply("Teeth whitening is $150.", 0.9, false);
        });

        worker.onMessage(new AIReplyMessage(TENANT_ID, CONVO_ID, CUSTOMER_ID));

        assertThat(captured.get().retrievedChunks()).hasSize(1);
        assertThat(captured.get().retrievedChunks().get(0).content())
            .contains("teeth whitening at $150");
        assertThat(captured.get().retrievedChunks().get(0).documentTitle()).isEqualTo("FAQ");
    }

    private static Conversation botConvo() {
        Conversation c = new Conversation();
        c.setId(CONVO_ID);
        c.setTenantId(TENANT_ID);
        c.setCustomerId(CUSTOMER_ID);
        c.setStatus(ConversationStatus.BOT_ACTIVE);
        return c;
    }

    private static Tenant stubTenant(String systemPrompt, Map<String, Object> chatCfg) {
        Tenant t = new Tenant();
        t.setId(TENANT_ID);
        t.setName("Acme");
        t.setAiSystemPrompt(systemPrompt);
        t.setChatConfig(chatCfg == null ? new HashMap<>() : chatCfg);
        return t;
    }

    private static Customer stubCustomer() {
        Customer c = new Customer();
        c.setId(CUSTOMER_ID);
        c.setTenantId(TENANT_ID);
        c.setPhoneE164("+14155557777");
        c.setFullName("Test Customer");
        return c;
    }

    private static Page<Message> stubHistory(String latestCustomerMessage) {
        Message m = new Message();
        m.setId(UUID.randomUUID());
        m.setTenantId(TENANT_ID);
        m.setCustomerId(CUSTOMER_ID);
        m.setDirection(MessageDirection.IN);
        m.setSenderType(SenderType.CUSTOMER);
        m.setBody(latestCustomerMessage);
        m.setStatus(MessageStatus.DELIVERED);
        return new PageImpl<>(List.of(m));
    }
}
