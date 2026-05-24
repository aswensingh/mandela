package com.marketinghub.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_events")
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "whatsapp_message_id", nullable = false, unique = true)
    private String whatsappMessageId;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getWhatsappMessageId() { return whatsappMessageId; }
    public void setWhatsappMessageId(String whatsappMessageId) { this.whatsappMessageId = whatsappMessageId; }

    public Instant getProcessedAt() { return processedAt; }
}
