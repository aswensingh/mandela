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
import com.marketinghub.whatsapp.WhatsAppApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pulls each {@link AIReplyMessage} off the ai.reply queue, asks {@link AIChatClient} for
 * a reply, and either (a) hands the conversation off to a human or (b) sends the reply via
 * WhatsApp and records it as a BOT outbound message.
 */
@Component
public class AIReplyWorker {

    private static final Logger log = LoggerFactory.getLogger(AIReplyWorker.class);

    private final AIChatClient chatClient;
    private final TenantRepository tenantRepository;
    private final ConversationRepository conversationRepository;
    private final CustomerRepository customerRepository;
    private final MessageRepository messageRepository;
    private final EncryptionService encryptionService;
    private final WhatsAppApiClient apiClient;
    private final VectorStore vectorStore;
    private final TransactionTemplate tx;

    private final int historyWindow;
    private final double defaultHandoffConfidence;
    private final String defaultSystemPrompt;
    private final int ragTopK;
    private final double ragSimilarityThreshold;

    public AIReplyWorker(
        AIChatClient chatClient,
        TenantRepository tenantRepository,
        ConversationRepository conversationRepository,
        CustomerRepository customerRepository,
        MessageRepository messageRepository,
        EncryptionService encryptionService,
        WhatsAppApiClient apiClient,
        VectorStore vectorStore,
        PlatformTransactionManager transactionManager,
        @Value("${ai.history-window:10}") int historyWindow,
        @Value("${ai.default-handoff-confidence:0.5}") double defaultHandoffConfidence,
        @Value("${ai.default-system-prompt:You are a helpful assistant.}") String defaultSystemPrompt,
        @Value("${ai.rag.top-k:4}") int ragTopK,
        @Value("${ai.rag.similarity-threshold:0.0}") double ragSimilarityThreshold
    ) {
        this.chatClient = chatClient;
        this.tenantRepository = tenantRepository;
        this.conversationRepository = conversationRepository;
        this.customerRepository = customerRepository;
        this.messageRepository = messageRepository;
        this.encryptionService = encryptionService;
        this.apiClient = apiClient;
        this.vectorStore = vectorStore;
        this.tx = new TransactionTemplate(transactionManager);
        this.historyWindow = historyWindow;
        this.defaultHandoffConfidence = defaultHandoffConfidence;
        this.defaultSystemPrompt = defaultSystemPrompt;
        this.ragTopK = ragTopK;
        this.ragSimilarityThreshold = ragSimilarityThreshold;
    }

    @RabbitListener(queues = AIAmqpConfig.AI_REPLY_QUEUE)
    public void onMessage(AIReplyMessage envelope) {
        try {
            handle(envelope);
        } catch (RuntimeException e) {
            // Catch-all so a transient blow-up doesn't requeue forever. The conversation
            // stays in BOT_ACTIVE; the next inbound triggers another attempt.
            log.warn("AI reply failed for conversation {}: {}", envelope.conversationId(), e.getMessage(), e);
        }
    }

    private void handle(AIReplyMessage envelope) {
        // Snapshot the context inside a read tx so the worker sees committed state.
        Snapshot snap = tx.execute(s -> loadSnapshot(envelope));
        if (snap == null) {
            log.debug("AI reply: snapshot null for {} — skipping", envelope.conversationId());
            return;
        }
        if (snap.conversation.getStatus() != ConversationStatus.BOT_ACTIVE) {
            log.debug("AI reply: conversation {} is no longer BOT_ACTIVE — skipping",
                envelope.conversationId());
            return;
        }

        // Retrieve tenant-scoped knowledge chunks for the customer's latest message.
        String latestCustomer = latestCustomerMessageOrEmpty(snap.history);
        List<AIChatRequest.RetrievedChunk> retrieved = retrieveChunks(envelope.tenantId(), latestCustomer);

        AIChatRequest req = new AIChatRequest(
            envelope.tenantId(),
            envelope.conversationId(),
            effectiveSystemPrompt(snap.tenant),
            snap.customer == null ? null : snap.customer.getFullName(),
            snap.customer == null ? null : snap.customer.getPhoneE164(),
            snap.history,
            retrieved
        );

        BotReply reply = chatClient.generateReply(req);
        double threshold = handoffThreshold(snap.tenant);

        if (reply.requestHandoff() || reply.confidence() < threshold) {
            log.info("AI reply: conversation {} -> HUMAN_ACTIVE (handoff={}, confidence={})",
                envelope.conversationId(), reply.requestHandoff(), reply.confidence());
            tx.executeWithoutResult(s -> {
                conversationRepository.findById(envelope.conversationId()).ifPresent(c -> {
                    if (c.getStatus() == ConversationStatus.BOT_ACTIVE) {
                        c.setStatus(ConversationStatus.HUMAN_ACTIVE);
                    }
                });
            });
            return;
        }

        // Send via WhatsApp (mock or real).
        String phoneNumberId = null;
        String accessToken = null;
        if (!apiClient.isMockMode()) {
            if (snap.tenant.getWhatsappPhoneNumberId() == null
                || snap.tenant.getWhatsappAccessTokenEncrypted() == null) {
                log.warn("AI reply for tenant {} cannot send: WhatsApp not configured", envelope.tenantId());
                return;
            }
            phoneNumberId = snap.tenant.getWhatsappPhoneNumberId();
            accessToken = encryptionService.decrypt(snap.tenant.getWhatsappAccessTokenEncrypted());
        }

        String wamid;
        try {
            wamid = apiClient.sendText(
                phoneNumberId, accessToken,
                snap.customer == null ? null : snap.customer.getPhoneE164(),
                reply.reply());
        } catch (WhatsAppApiException e) {
            log.warn("AI reply: WhatsApp send failed for conversation {}: {}",
                envelope.conversationId(), e.getMessage());
            tx.executeWithoutResult(s -> recordFailedBot(envelope, reply.reply(), e.getMessage()));
            return;
        }

        tx.executeWithoutResult(s -> recordSentBot(envelope, reply, wamid));
        log.info("AI reply: conversation {} -> BOT replied (confidence={}, wamid={})",
            envelope.conversationId(), reply.confidence(), wamid);
    }

    private Snapshot loadSnapshot(AIReplyMessage envelope) {
        Optional<Conversation> co = conversationRepository.findById(envelope.conversationId());
        if (co.isEmpty()) return null;
        Conversation conversation = co.get();
        if (!conversation.getTenantId().equals(envelope.tenantId())) return null;

        Optional<Tenant> to = tenantRepository.findById(envelope.tenantId());
        if (to.isEmpty()) return null;
        Tenant tenant = to.get();

        Customer customer = customerRepository.findById(envelope.customerId()).orElse(null);

        var page = messageRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAsc(
            envelope.tenantId(), envelope.customerId(),
            PageRequest.of(0, historyWindow, Sort.by(Sort.Order.desc("createdAt"))));
        List<Message> latest = new ArrayList<>(page.getContent());
        // The page comes back newest-first when we sort desc; reverse to chronological.
        java.util.Collections.reverse(latest);

        List<AIChatRequest.HistoryMessage> history = new ArrayList<>(latest.size());
        for (Message m : latest) {
            AIChatRequest.Role role = switch (m.getSenderType()) {
                case CUSTOMER -> AIChatRequest.Role.CUSTOMER;
                case BOT -> AIChatRequest.Role.BOT;
                case AGENT -> AIChatRequest.Role.AGENT;
                case SYSTEM -> AIChatRequest.Role.SYSTEM;
            };
            history.add(new AIChatRequest.HistoryMessage(role, m.getBody()));
        }

        return new Snapshot(tenant, conversation, customer, history);
    }

    private List<AIChatRequest.RetrievedChunk> retrieveChunks(UUID tenantId, String query) {
        if (query == null || query.isBlank()) return List.of();
        try {
            // Single-quotes inside the filter expression escape via the language's own rules,
            // but UUIDs only contain [0-9a-f-] so we can interpolate safely.
            SearchRequest req = SearchRequest.builder()
                .query(query)
                .topK(ragTopK)
                .similarityThreshold(ragSimilarityThreshold)
                .filterExpression("tenant_id == '" + tenantId + "'")
                .build();
            List<Document> docs = vectorStore.similaritySearch(req);
            if (docs == null || docs.isEmpty()) return List.of();
            List<AIChatRequest.RetrievedChunk> out = new ArrayList<>(docs.size());
            for (Document d : docs) {
                String title = d.getMetadata() == null ? null
                    : String.valueOf(d.getMetadata().getOrDefault("document_title", ""));
                Object scoreObj = d.getMetadata() == null ? null
                    : d.getMetadata().get("distance");
                double score = scoreObj instanceof Number n ? n.doubleValue() : 0.0;
                out.add(new AIChatRequest.RetrievedChunk(d.getText(), title, score));
            }
            return out;
        } catch (Exception e) {
            log.warn("RAG retrieval failed for tenant {}: {}", tenantId, e.getMessage());
            return List.of();
        }
    }

    private static String latestCustomerMessageOrEmpty(List<AIChatRequest.HistoryMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).role() == AIChatRequest.Role.CUSTOMER) return history.get(i).body();
        }
        return "";
    }

    private String effectiveSystemPrompt(Tenant tenant) {
        if (tenant.getAiSystemPrompt() != null && !tenant.getAiSystemPrompt().isBlank()) {
            return tenant.getAiSystemPrompt();
        }
        return defaultSystemPrompt;
    }

    private double handoffThreshold(Tenant tenant) {
        if (tenant.getChatConfig() != null) {
            Object v = tenant.getChatConfig().get("handoff_confidence_threshold");
            if (v instanceof Number n) return n.doubleValue();
        }
        return defaultHandoffConfidence;
    }

    private void recordSentBot(AIReplyMessage envelope, BotReply reply, String wamid) {
        Message m = new Message();
        m.setTenantId(envelope.tenantId());
        m.setCustomerId(envelope.customerId());
        m.setDirection(MessageDirection.OUT);
        m.setSenderType(SenderType.BOT);
        m.setBody(reply.reply());
        m.setStatus(MessageStatus.SENT);
        m.setWhatsappMessageId(wamid);
        messageRepository.save(m);

        conversationRepository.findById(envelope.conversationId()).ifPresent(c -> {
            c.setLastMessageAt(Instant.now());
        });
    }

    private void recordFailedBot(AIReplyMessage envelope, String body, String error) {
        Message m = new Message();
        m.setTenantId(envelope.tenantId());
        m.setCustomerId(envelope.customerId());
        m.setDirection(MessageDirection.OUT);
        m.setSenderType(SenderType.BOT);
        m.setBody(body);
        m.setStatus(MessageStatus.FAILED);
        m.setErrorMessage(truncate(error, 1000));
        messageRepository.save(m);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private record Snapshot(
        Tenant tenant,
        Conversation conversation,
        Customer customer,
        List<AIChatRequest.HistoryMessage> history
    ) {}
}
