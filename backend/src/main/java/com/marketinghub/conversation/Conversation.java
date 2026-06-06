package com.marketinghub.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ConversationStatus status = ConversationStatus.BOT_ACTIVE;

    @Column(name = "assigned_agent_id")
    private UUID assignedAgentId;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "bot_context_reset_at")
    private Instant botContextResetAt;

    // Why the conversation is in human mode + the AI confidence at that moment (debug aid).
    // MODEL_REQUESTED / LOW_CONFIDENCE (auto), MANUAL_TAKEOVER (agent), or null (bot-owned).
    @Column(name = "handoff_reason")
    private String handoffReason;

    @Column(name = "handoff_confidence")
    private Double handoffConfidence;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }

    public ConversationStatus getStatus() { return status; }
    public void setStatus(ConversationStatus status) { this.status = status; }

    public UUID getAssignedAgentId() { return assignedAgentId; }
    public void setAssignedAgentId(UUID assignedAgentId) { this.assignedAgentId = assignedAgentId; }

    public Instant getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(Instant lastMessageAt) { this.lastMessageAt = lastMessageAt; }

    public Instant getBotContextResetAt() { return botContextResetAt; }
    public void setBotContextResetAt(Instant botContextResetAt) { this.botContextResetAt = botContextResetAt; }

    public String getHandoffReason() { return handoffReason; }
    public void setHandoffReason(String handoffReason) { this.handoffReason = handoffReason; }

    public Double getHandoffConfidence() { return handoffConfidence; }
    public void setHandoffConfidence(Double handoffConfidence) { this.handoffConfidence = handoffConfidence; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
