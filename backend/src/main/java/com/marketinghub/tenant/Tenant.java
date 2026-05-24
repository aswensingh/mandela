package com.marketinghub.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "industry")
    private String industry;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TenantStatus status = TenantStatus.ACTIVE;

    @Column(name = "whatsapp_phone_number_id")
    private String whatsappPhoneNumberId;

    @Column(name = "whatsapp_access_token_encrypted")
    private byte[] whatsappAccessTokenEncrypted;

    @Column(name = "ai_system_prompt")
    private String aiSystemPrompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "chat_config", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> chatConfig = new HashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public TenantStatus getStatus() { return status; }
    public void setStatus(TenantStatus status) { this.status = status; }

    public String getWhatsappPhoneNumberId() { return whatsappPhoneNumberId; }
    public void setWhatsappPhoneNumberId(String whatsappPhoneNumberId) { this.whatsappPhoneNumberId = whatsappPhoneNumberId; }

    public byte[] getWhatsappAccessTokenEncrypted() { return whatsappAccessTokenEncrypted; }
    public void setWhatsappAccessTokenEncrypted(byte[] whatsappAccessTokenEncrypted) { this.whatsappAccessTokenEncrypted = whatsappAccessTokenEncrypted; }

    public String getAiSystemPrompt() { return aiSystemPrompt; }
    public void setAiSystemPrompt(String aiSystemPrompt) { this.aiSystemPrompt = aiSystemPrompt; }

    public Map<String, Object> getChatConfig() { return chatConfig; }
    public void setChatConfig(Map<String, Object> chatConfig) { this.chatConfig = chatConfig; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
