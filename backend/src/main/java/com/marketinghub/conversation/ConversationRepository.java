package com.marketinghub.conversation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByTenantIdAndCustomerId(UUID tenantId, UUID customerId);

    Optional<Conversation> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Conversation> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<Conversation> findAllByTenantIdAndStatus(UUID tenantId, ConversationStatus status, Pageable pageable);

    /** "Open" tab in the UI = anything that isn't CLOSED (BOT_ACTIVE or HUMAN_ACTIVE). */
    Page<Conversation> findAllByTenantIdAndStatusNot(UUID tenantId, ConversationStatus excluded, Pageable pageable);

    Page<Conversation> findAllByTenantIdAndAssignedAgentId(UUID tenantId, UUID assignedAgentId, Pageable pageable);
}
