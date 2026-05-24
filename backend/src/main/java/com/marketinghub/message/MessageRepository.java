package com.marketinghub.message;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Optional<Message> findByWhatsappMessageId(String whatsappMessageId);

    Page<Message> findAllByTenantIdAndCustomerIdOrderByCreatedAtAsc(
        UUID tenantId, UUID customerId, Pageable pageable);

    /**
     * Postgres DISTINCT ON: returns at most one row per customer_id — the latest message.
     * Used to populate the conversation list preview without N+1 queries.
     */
    @Query(
        value = "SELECT DISTINCT ON (customer_id) * FROM messages "
              + "WHERE tenant_id = :tenantId AND customer_id IN (:customerIds) "
              + "ORDER BY customer_id, created_at DESC",
        nativeQuery = true
    )
    List<Message> findLatestPerCustomer(
        @Param("tenantId") UUID tenantId,
        @Param("customerIds") Collection<UUID> customerIds
    );
}
