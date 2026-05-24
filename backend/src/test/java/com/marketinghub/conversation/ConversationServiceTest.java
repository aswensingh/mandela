package com.marketinghub.conversation;

import com.marketinghub.auth.AuthenticatedPrincipal;
import com.marketinghub.auth.UserRole;
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
import com.marketinghub.tenant.TenantContext;
import com.marketinghub.tenant.TenantRepository;
import com.marketinghub.whatsapp.WhatsAppApiClient;
import com.marketinghub.whatsapp.WhatsAppApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private EncryptionService encryptionService;
    @Mock private WhatsAppApiClient whatsAppApiClient;

    private ConversationService service;

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ConversationService(
            conversationRepository, customerRepository, messageRepository,
            tenantRepository, encryptionService, whatsAppApiClient);
        TenantContext.setTenantId(TENANT);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedPrincipal(USER, "u@a.com", TENANT, UserRole.AGENT),
                null,
                List.of()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_all_returnsItemsWithCustomerAndPreview() {
        UUID convoId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Conversation c = stubConversation(convoId, customerId, ConversationStatus.BOT_ACTIVE);
        Customer cust = stubCustomer(customerId, "+14155550101", "Alice");
        Message latest = stubMessage(customerId, "Hello there", MessageDirection.IN, SenderType.CUSTOMER);

        when(conversationRepository.findAllByTenantId(eq(TENANT), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(c)));
        when(customerRepository.findAllByIdInAndTenantId(anyCollection(), eq(TENANT)))
            .thenReturn(List.of(cust));
        when(messageRepository.findLatestPerCustomer(eq(TENANT), anyCollection()))
            .thenReturn(List.of(latest));

        Page<ConversationListItemDto> page = service.list(ConversationService.Filter.ALL, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(1);
        ConversationListItemDto dto = page.getContent().get(0);
        assertThat(dto.id()).isEqualTo(convoId);
        assertThat(dto.customerPhone()).isEqualTo("+14155550101");
        assertThat(dto.customerName()).isEqualTo("Alice");
        assertThat(dto.lastMessageBody()).isEqualTo("Hello there");
        assertThat(dto.lastMessageDirection()).isEqualTo(MessageDirection.IN);
        assertThat(dto.lastMessageSenderType()).isEqualTo(SenderType.CUSTOMER);
    }

    @Test
    void list_open_filtersOutClosed() {
        when(conversationRepository.findAllByTenantIdAndStatusNot(eq(TENANT), eq(ConversationStatus.CLOSED), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        service.list(ConversationService.Filter.OPEN, PageRequest.of(0, 20));
        verify(conversationRepository).findAllByTenantIdAndStatusNot(eq(TENANT), eq(ConversationStatus.CLOSED), any(Pageable.class));
    }

    @Test
    void list_closed_filtersByStatus() {
        when(conversationRepository.findAllByTenantIdAndStatus(eq(TENANT), eq(ConversationStatus.CLOSED), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        service.list(ConversationService.Filter.CLOSED, PageRequest.of(0, 20));
        verify(conversationRepository).findAllByTenantIdAndStatus(eq(TENANT), eq(ConversationStatus.CLOSED), any(Pageable.class));
    }

    @Test
    void list_mine_filtersByCurrentUser() {
        when(conversationRepository.findAllByTenantIdAndAssignedAgentId(eq(TENANT), eq(USER), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        service.list(ConversationService.Filter.MINE, PageRequest.of(0, 20));
        verify(conversationRepository).findAllByTenantIdAndAssignedAgentId(eq(TENANT), eq(USER), any(Pageable.class));
    }

    @Test
    void list_truncatesLongPreview() {
        UUID convoId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Conversation c = stubConversation(convoId, customerId, ConversationStatus.BOT_ACTIVE);
        String longBody = "a".repeat(500);
        Message latest = stubMessage(customerId, longBody, MessageDirection.IN, SenderType.CUSTOMER);

        when(conversationRepository.findAllByTenantId(eq(TENANT), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(c)));
        when(customerRepository.findAllByIdInAndTenantId(anyCollection(), eq(TENANT)))
            .thenReturn(List.of(stubCustomer(customerId, "+1", null)));
        when(messageRepository.findLatestPerCustomer(eq(TENANT), anyCollection()))
            .thenReturn(List.of(latest));

        ConversationListItemDto dto = service.list(ConversationService.Filter.ALL, PageRequest.of(0, 20))
            .getContent().get(0);
        assertThat(dto.lastMessageBody()).hasSize(200);
    }

    @Test
    void list_emptyPage_doesNotQueryDownstream() {
        when(conversationRepository.findAllByTenantId(eq(TENANT), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        Page<ConversationListItemDto> page =
            service.list(ConversationService.Filter.ALL, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isZero();
        // No customer or message lookups happen
        verify(customerRepository, org.mockito.Mockito.never()).findAllByIdInAndTenantId(any(), any());
        verify(messageRepository, org.mockito.Mockito.never()).findLatestPerCustomer(any(), any());
    }

    @Test
    void get_otherTenant_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(conversationRepository.findByIdAndTenantId(id, TENANT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void listMessages_otherTenant_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(conversationRepository.findByIdAndTenantId(id, TENANT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listMessages(id, PageRequest.of(0, 20)))
            .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void listMessages_returnsChronologicalMessages() {
        UUID convoId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Conversation c = stubConversation(convoId, customerId, ConversationStatus.BOT_ACTIVE);
        Message m1 = stubMessage(customerId, "first", MessageDirection.IN, SenderType.CUSTOMER);
        Message m2 = stubMessage(customerId, "second", MessageDirection.OUT, SenderType.AGENT);
        when(conversationRepository.findByIdAndTenantId(convoId, TENANT)).thenReturn(Optional.of(c));
        when(messageRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAsc(
            eq(TENANT), eq(customerId), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(m1, m2)));

        Page<ConversationMessageDto> page = service.listMessages(convoId, PageRequest.of(0, 100));
        assertThat(page.getContent()).extracting(ConversationMessageDto::body)
            .containsExactly("first", "second");
        assertThat(page.getContent()).extracting(ConversationMessageDto::direction)
            .containsExactly(MessageDirection.IN, MessageDirection.OUT);
    }

    @Test
    void list_requiresTenantContext() {
        TenantContext.clear();
        assertThatThrownBy(() -> service.list(ConversationService.Filter.ALL, PageRequest.of(0, 20)))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listMine_requiresAuthenticatedPrincipal() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.list(ConversationService.Filter.MINE, PageRequest.of(0, 20)))
            .isInstanceOf(AccessDeniedException.class);
    }

    // ---------- Phase 18: agent takeover / release / send ----------

    @Test
    void takeOver_flipsToHumanActive_andAssignsCurrentUser() {
        UUID convoId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Conversation c = stubConversation(convoId, customerId, ConversationStatus.BOT_ACTIVE);
        when(conversationRepository.findByIdAndTenantId(convoId, TENANT)).thenReturn(Optional.of(c));
        when(customerRepository.findByIdAndTenantId(customerId, TENANT))
            .thenReturn(Optional.of(stubCustomer(customerId, "+15555550800", "Alice")));
        when(messageRepository.findLatestPerCustomer(eq(TENANT), anyCollection()))
            .thenReturn(List.of());

        ConversationListItemDto dto = service.takeOver(convoId);

        assertThat(c.getStatus()).isEqualTo(ConversationStatus.HUMAN_ACTIVE);
        assertThat(c.getAssignedAgentId()).isEqualTo(USER);
        assertThat(dto.status()).isEqualTo(ConversationStatus.HUMAN_ACTIVE);
        assertThat(dto.assignedAgentId()).isEqualTo(USER);
    }

    @Test
    void takeOver_rejectsClosedConversation() {
        UUID convoId = UUID.randomUUID();
        Conversation c = stubConversation(convoId, UUID.randomUUID(), ConversationStatus.CLOSED);
        when(conversationRepository.findByIdAndTenantId(convoId, TENANT)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.takeOver(convoId))
            .isInstanceOf(InvalidConversationStateException.class);
    }

    @Test
    void release_clearsAssigneeAndReturnsToBot() {
        UUID convoId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Conversation c = stubConversation(convoId, customerId, ConversationStatus.HUMAN_ACTIVE);
        c.setAssignedAgentId(USER);
        when(conversationRepository.findByIdAndTenantId(convoId, TENANT)).thenReturn(Optional.of(c));
        when(customerRepository.findByIdAndTenantId(customerId, TENANT))
            .thenReturn(Optional.of(stubCustomer(customerId, "+15555550800", "Alice")));
        when(messageRepository.findLatestPerCustomer(eq(TENANT), anyCollection()))
            .thenReturn(List.of());

        ConversationListItemDto dto = service.release(convoId);

        assertThat(c.getStatus()).isEqualTo(ConversationStatus.BOT_ACTIVE);
        assertThat(c.getAssignedAgentId()).isNull();
        assertThat(dto.status()).isEqualTo(ConversationStatus.BOT_ACTIVE);
        assertThat(dto.assignedAgentId()).isNull();
    }

    @Test
    void sendAgentMessage_happyPath_inMockMode_storesAgentRow() {
        UUID convoId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Conversation c = stubConversation(convoId, customerId, ConversationStatus.HUMAN_ACTIVE);
        when(conversationRepository.findByIdAndTenantId(convoId, TENANT)).thenReturn(Optional.of(c));
        when(customerRepository.findByIdAndTenantId(customerId, TENANT))
            .thenReturn(Optional.of(stubCustomer(customerId, "+15555550800", "Alice")));
        when(whatsAppApiClient.isMockMode()).thenReturn(true);
        when(whatsAppApiClient.sendText(any(), any(), eq("+15555550800"), eq("ok, on it")))
            .thenReturn("wamid.MOCK-agent-1");
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        ConversationMessageDto dto = service.sendAgentMessage(convoId, "ok, on it");

        assertThat(dto.direction()).isEqualTo(MessageDirection.OUT);
        assertThat(dto.senderType()).isEqualTo(SenderType.AGENT);
        assertThat(dto.body()).isEqualTo("ok, on it");
        assertThat(dto.status()).isEqualTo(MessageStatus.SENT);
        assertThat(dto.whatsappMessageId()).isEqualTo("wamid.MOCK-agent-1");
        // Conversation gets the assignee + last_message_at touched.
        assertThat(c.getAssignedAgentId()).isEqualTo(USER);
        assertThat(c.getLastMessageAt()).isNotNull();
    }

    @Test
    void sendAgentMessage_rejectsWhenNotHumanActive() {
        UUID convoId = UUID.randomUUID();
        Conversation c = stubConversation(convoId, UUID.randomUUID(), ConversationStatus.BOT_ACTIVE);
        when(conversationRepository.findByIdAndTenantId(convoId, TENANT)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.sendAgentMessage(convoId, "hello"))
            .isInstanceOf(InvalidConversationStateException.class);
    }

    @Test
    void sendAgentMessage_metaFailure_storesFailedRow_andBubblesUp() {
        UUID convoId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Conversation c = stubConversation(convoId, customerId, ConversationStatus.HUMAN_ACTIVE);
        when(conversationRepository.findByIdAndTenantId(convoId, TENANT)).thenReturn(Optional.of(c));
        when(customerRepository.findByIdAndTenantId(customerId, TENANT))
            .thenReturn(Optional.of(stubCustomer(customerId, "+15555550800", "Alice")));
        when(whatsAppApiClient.isMockMode()).thenReturn(true);
        when(whatsAppApiClient.sendText(any(), any(), any(), any()))
            .thenThrow(new WhatsAppApiException("Meta API 400: nope"));

        Message[] savedHolder = new Message[1];
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            savedHolder[0] = m;
            return m;
        });

        assertThatThrownBy(() -> service.sendAgentMessage(convoId, "hello"))
            .isInstanceOf(WhatsAppApiException.class);

        assertThat(savedHolder[0]).isNotNull();
        assertThat(savedHolder[0].getStatus()).isEqualTo(MessageStatus.FAILED);
        assertThat(savedHolder[0].getErrorMessage()).contains("Meta API 400");
        assertThat(savedHolder[0].getWhatsappMessageId()).isNull();
        // Verify the message was persisted exactly once (failure path).
        verify(messageRepository).save(any(Message.class));
    }

    private static Conversation stubConversation(UUID id, UUID customerId, ConversationStatus status) {
        Conversation c = new Conversation();
        c.setId(id);
        c.setTenantId(TENANT);
        c.setCustomerId(customerId);
        c.setStatus(status);
        return c;
    }

    private static Customer stubCustomer(UUID id, String phone, String name) {
        Customer c = new Customer();
        c.setId(id);
        c.setTenantId(TENANT);
        c.setPhoneE164(phone);
        c.setFullName(name);
        return c;
    }

    private static Message stubMessage(UUID customerId, String body, MessageDirection dir, SenderType sender) {
        Message m = new Message();
        m.setId(UUID.randomUUID());
        m.setTenantId(TENANT);
        m.setCustomerId(customerId);
        m.setBody(body);
        m.setDirection(dir);
        m.setSenderType(sender);
        m.setStatus(MessageStatus.DELIVERED);
        return m;
    }
}
