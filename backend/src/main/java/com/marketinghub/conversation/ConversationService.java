package com.marketinghub.conversation;

import com.marketinghub.auth.AuthenticatedPrincipal;
import com.marketinghub.auth.UserRepository;
import com.marketinghub.common.crypto.EncryptionService;
import com.marketinghub.conversation.dto.ConversationListItemDto;
import com.marketinghub.conversation.dto.ConversationMessageDto;
import com.marketinghub.customer.Customer;
import com.marketinghub.customer.CustomerRepository;
import com.marketinghub.message.Message;
import com.marketinghub.message.MessageDirection;
import com.marketinghub.message.MessageRepository;
import com.marketinghub.message.MessageStatus;
import com.marketinghub.message.SenderType;
import com.marketinghub.tenant.Tenant;
import com.marketinghub.tenant.TenantContext;
import com.marketinghub.tenant.TenantRepository;
import com.marketinghub.whatsapp.WhatsAppApiClient;
import com.marketinghub.whatsapp.WhatsAppApiException;
import com.marketinghub.whatsapp.WhatsAppNotConfiguredException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ConversationService {

    public enum Filter {
        ALL, MINE, OPEN, CLOSED
    }

    private static final int PREVIEW_MAX = 200;

    private final ConversationRepository conversationRepository;
    private final CustomerRepository customerRepository;
    private final MessageRepository messageRepository;
    private final TenantRepository tenantRepository;
    private final EncryptionService encryptionService;
    private final WhatsAppApiClient whatsAppApiClient;
    private final UserRepository userRepository;
    private final String agentPrefix;
    private final String agentSuffix;

    public ConversationService(
        ConversationRepository conversationRepository,
        CustomerRepository customerRepository,
        MessageRepository messageRepository,
        TenantRepository tenantRepository,
        EncryptionService encryptionService,
        WhatsAppApiClient whatsAppApiClient,
        UserRepository userRepository,
        @Value("${messaging.sender-label.agent-prefix:}") String agentPrefix,
        @Value("${messaging.sender-label.agent-suffix:}") String agentSuffix
    ) {
        this.conversationRepository = conversationRepository;
        this.customerRepository = customerRepository;
        this.messageRepository = messageRepository;
        this.tenantRepository = tenantRepository;
        this.encryptionService = encryptionService;
        this.whatsAppApiClient = whatsAppApiClient;
        this.userRepository = userRepository;
        this.agentPrefix = agentPrefix == null ? "" : agentPrefix;
        this.agentSuffix = agentSuffix == null ? "" : agentSuffix;
    }

    @Transactional(readOnly = true)
    public Page<ConversationListItemDto> list(Filter filter, Pageable pageable) {
        UUID tenantId = requireTenant();
        Pageable sorted = ensureSort(pageable);
        Page<Conversation> page = switch (filter == null ? Filter.ALL : filter) {
            case ALL -> conversationRepository.findAllByTenantId(tenantId, sorted);
            // "Open" tab post-Phase-16 = anything not CLOSED (BOT_ACTIVE or HUMAN_ACTIVE).
            case OPEN -> conversationRepository.findAllByTenantIdAndStatusNot(tenantId, ConversationStatus.CLOSED, sorted);
            case CLOSED -> conversationRepository.findAllByTenantIdAndStatus(tenantId, ConversationStatus.CLOSED, sorted);
            case MINE -> conversationRepository.findAllByTenantIdAndAssignedAgentId(tenantId, currentUserId(), sorted);
        };
        if (page.isEmpty()) {
            return page.map(c -> toListItem(c, null, null));
        }

        Set<UUID> customerIds = new HashSet<>();
        page.forEach(c -> customerIds.add(c.getCustomerId()));
        Map<UUID, Customer> customersById = new HashMap<>();
        customerRepository.findAllByIdInAndTenantId(customerIds, tenantId)
            .forEach(c -> customersById.put(c.getId(), c));

        // Latest message per (tenant, customer) for preview text.
        Map<UUID, Message> latestByCustomer = new HashMap<>();
        messageRepository.findLatestPerCustomer(tenantId, customerIds)
            .forEach(m -> latestByCustomer.put(m.getCustomerId(), m));

        return page.map(c -> toListItem(
            c, customersById.get(c.getCustomerId()), latestByCustomer.get(c.getCustomerId())));
    }

    /**
     * Deletes a customer's entire conversation: all messages plus the conversation row.
     * The customer record itself is kept (so you can re-test with the same number). Handy
     * for clearing a chat to a clean slate. Unlike "reset bot context" — which keeps the
     * messages and only hides them from the LLM — this permanently removes the thread.
     */
    @Transactional
    public void deleteConversation(UUID id) {
        UUID tenantId = requireTenant();
        Conversation c = conversationRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ConversationNotFoundException(id));
        messageRepository.deleteByTenantIdAndCustomerId(tenantId, c.getCustomerId());
        conversationRepository.delete(c);
    }

    @Transactional(readOnly = true)
    public ConversationListItemDto get(UUID id) {
        UUID tenantId = requireTenant();
        Conversation c = conversationRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ConversationNotFoundException(id));
        return reloadAsListItem(c, tenantId);
    }

    @Transactional(readOnly = true)
    public Page<ConversationMessageDto> listMessages(UUID conversationId, Pageable pageable) {
        UUID tenantId = requireTenant();
        Conversation c = conversationRepository.findByIdAndTenantId(conversationId, tenantId)
            .orElseThrow(() -> new ConversationNotFoundException(conversationId));
        Pageable chronological = ensureChronologicalSort(pageable);
        return messageRepository
            .findAllByTenantIdAndCustomerIdOrderByCreatedAtAsc(tenantId, c.getCustomerId(), chronological)
            .map(ConversationService::toMessageDto);
    }

    // ---------- Phase 18: agent takeover / release / send ----------

    /**
     * Agent takes over a conversation from the bot. Flips status to HUMAN_ACTIVE and
     * assigns the current authenticated user as the agent. Downstream effect: the
     * AIReplyWorker checks the conversation status before generating a reply and skips
     * when HUMAN_ACTIVE — that's how takeover silences the bot.
     */
    @Transactional
    public ConversationListItemDto takeOver(UUID id) {
        UUID tenantId = requireTenant();
        Conversation c = conversationRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ConversationNotFoundException(id));
        if (c.getStatus() == ConversationStatus.CLOSED) {
            throw new InvalidConversationStateException(
                "Cannot take over a CLOSED conversation");
        }
        c.setStatus(ConversationStatus.HUMAN_ACTIVE);
        c.setAssignedAgentId(currentUserId());
        c.setHandoffReason("MANUAL_TAKEOVER");
        c.setHandoffConfidence(null);
        conversationRepository.save(c);
        return reloadAsListItem(c, tenantId);
    }

    /**
     * Agent releases the conversation back to the bot. Clears the assignee and flips to
     * BOT_ACTIVE. CLOSED conversations can't be released (reopening is out of scope here).
     */
    @Transactional
    public ConversationListItemDto release(UUID id) {
        UUID tenantId = requireTenant();
        Conversation c = conversationRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ConversationNotFoundException(id));
        if (c.getStatus() == ConversationStatus.CLOSED) {
            throw new InvalidConversationStateException(
                "Cannot release a CLOSED conversation");
        }
        c.setStatus(ConversationStatus.BOT_ACTIVE);
        c.setAssignedAgentId(null);
        c.setHandoffReason(null);
        c.setHandoffConfidence(null);
        conversationRepository.save(c);
        return reloadAsListItem(c, tenantId);
    }

    /**
     * Wipes the bot's running context for this conversation. We set bot_context_reset_at
     * to "now" — the AIReplyWorker only feeds messages with created_at > that timestamp
     * into the LLM, so the bot effectively starts fresh on the next inbound. The visible
     * UI history is intentionally untouched.
     */
    @Transactional
    public ConversationListItemDto resetBotContext(UUID id) {
        UUID tenantId = requireTenant();
        Conversation c = conversationRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ConversationNotFoundException(id));
        if (c.getStatus() == ConversationStatus.CLOSED) {
            throw new InvalidConversationStateException(
                "Cannot reset bot context on a CLOSED conversation");
        }
        c.setBotContextResetAt(Instant.now());
        conversationRepository.save(c);
        return reloadAsListItem(c, tenantId);
    }

    /**
     * Agent sends a manual WhatsApp message to the customer. Requires the conversation be
     * in HUMAN_ACTIVE state — sending while the bot owns the conversation is rejected
     * (the UI's Send button is disabled in that case, but we enforce server-side too).
     *
     * On Meta success the message is stored as SENT with the returned wamid; on failure
     * the row is stored as FAILED with the error message, and the WhatsAppApiException
     * bubbles up so the controller surfaces a 502 (via GlobalExceptionHandler).
     */
    @Transactional
    public ConversationMessageDto sendAgentMessage(UUID id, String body) {
        UUID tenantId = requireTenant();
        Conversation c = conversationRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ConversationNotFoundException(id));
        if (c.getStatus() != ConversationStatus.HUMAN_ACTIVE) {
            throw new InvalidConversationStateException(
                "Cannot send agent message — conversation is " + c.getStatus()
                    + " (take over first)");
        }
        Customer customer = customerRepository.findByIdAndTenantId(c.getCustomerId(), tenantId)
            .orElseThrow(() -> new ConversationNotFoundException(id));

        // In mock mode we don't need tenant creds at all — WhatsAppApiClient.sendText
        // short-circuits and returns a fake wamid regardless of phone/token args.
        String phoneNumberId = null;
        String accessToken = null;
        if (!whatsAppApiClient.isMockMode()) {
            Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                    "Tenant " + tenantId + " not found in DB"));
            phoneNumberId = tenant.getWhatsappPhoneNumberId();
            if (phoneNumberId == null || tenant.getWhatsappAccessTokenEncrypted() == null) {
                throw new WhatsAppNotConfiguredException();
            }
            accessToken = encryptionService.decrypt(tenant.getWhatsappAccessTokenEncrypted());
        }

        Message m = new Message();
        m.setTenantId(tenantId);
        m.setCustomerId(c.getCustomerId());
        m.setDirection(MessageDirection.OUT);
        m.setSenderType(SenderType.AGENT);
        m.setBody(body);

        try {
            // Label the customer-facing text with the agent's name so they know a human
            // (not the bot) is now replying. The stored body stays clean — the in-app
            // thread already shows an AGENT tag.
            String outbound = agentLabel() + body;
            String wamid = whatsAppApiClient.sendText(
                phoneNumberId, accessToken, customer.getPhoneE164(), outbound);
            m.setStatus(MessageStatus.SENT);
            m.setWhatsappMessageId(wamid);
        } catch (WhatsAppApiException e) {
            m.setStatus(MessageStatus.FAILED);
            m.setErrorMessage(e.getMessage());
            messageRepository.save(m);
            throw e;
        }

        Message saved = messageRepository.save(m);

        // Touch the conversation only on a successful send. Auto-claim if no agent was
        // previously assigned (covers the corner case where takeover happened on a
        // different tab/session and this server instance is seeing the convo fresh).
        c.setLastMessageAt(Instant.now());
        if (c.getAssignedAgentId() == null) {
            c.setAssignedAgentId(currentUserId());
        }
        conversationRepository.save(c);

        return toMessageDto(saved);
    }

    private ConversationListItemDto reloadAsListItem(Conversation c, UUID tenantId) {
        Customer customer = customerRepository.findByIdAndTenantId(c.getCustomerId(), tenantId).orElse(null);
        List<Message> latest = messageRepository.findLatestPerCustomer(tenantId, List.of(c.getCustomerId()));
        return toListItem(c, customer, latest.isEmpty() ? null : latest.get(0));
    }

    private static ConversationListItemDto toListItem(Conversation c, Customer cust, Message latest) {
        return new ConversationListItemDto(
            c.getId(),
            c.getStatus(),
            c.getAssignedAgentId(),
            c.getCustomerId(),
            cust == null ? null : cust.getPhoneE164(),
            cust == null ? null : cust.getFullName(),
            c.getLastMessageAt(),
            latest == null ? null : truncate(latest.getBody(), PREVIEW_MAX),
            latest == null ? null : latest.getDirection(),
            latest == null ? null : latest.getSenderType(),
            c.getCreatedAt(),
            c.getHandoffReason(),
            c.getHandoffConfidence()
        );
    }

    private static ConversationMessageDto toMessageDto(Message m) {
        return new ConversationMessageDto(
            m.getId(),
            m.getDirection(),
            m.getSenderType(),
            m.getBody(),
            m.getStatus(),
            m.getWhatsappMessageId(),
            m.getErrorMessage(),
            m.getAiConfidence(),
            m.getCreatedAt()
        );
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static Pageable ensureSort(Pageable pageable) {
        // Default: most recent first. Postgres places NULLs first under DESC by default,
        // which actually suits us — brand-new conversations float to the top.
        // (Sort.nullsLast() would be cleaner intent-wise but Spring Data JPA can't yet
        // translate null-precedence hints into the Criteria API.)
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.desc("lastMessageAt")));
        }
        return pageable;
    }

    private static Pageable ensureChronologicalSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.asc("createdAt")));
        }
        return pageable;
    }

    private UUID requireTenant() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("No tenant context — conversations are tenant-scoped");
        }
        return tenantId;
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            throw new AccessDeniedException("Not authenticated");
        }
        return principal.userId();
    }

    /**
     * Builds the customer-facing prefix for an agent message, e.g. "👤 Jane: ".
     * Returns "" when labelling is disabled (blank agent-prefix config). Prefers the
     * agent's full name, falling back to username, then a generic "Agent".
     */
    private String agentLabel() {
        if (agentPrefix.isBlank()) return "";
        String name = userRepository.findById(currentUserId())
            .map(u -> (u.getFullName() != null && !u.getFullName().isBlank())
                ? u.getFullName().trim()
                : u.getUsername())
            .orElse("Agent");
        return agentPrefix + name + agentSuffix + ": ";
    }
}
