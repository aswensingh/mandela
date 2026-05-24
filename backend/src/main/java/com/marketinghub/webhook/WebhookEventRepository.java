package com.marketinghub.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    Optional<WebhookEvent> findByWhatsappMessageId(String whatsappMessageId);

    boolean existsByWhatsappMessageId(String whatsappMessageId);

    /**
     * Race-safe dedup claim. Returns 1 if this is a new event (caller should process)
     * or 0 if it was already recorded (caller dedups). Native upsert avoids the
     * JPA constraint-violation exception that would otherwise poison the outer @Transactional.
     */
    @Modifying
    @Query(
        value = "INSERT INTO webhook_events (whatsapp_message_id) VALUES (:wamid) "
              + "ON CONFLICT (whatsapp_message_id) DO NOTHING",
        nativeQuery = true
    )
    int tryClaim(@Param("wamid") String wamid);
}
