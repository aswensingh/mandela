package com.marketinghub.ai;

import java.util.List;
import java.util.UUID;

/** Input bundle the worker hands to AIChatClient. */
public record AIChatRequest(
    UUID tenantId,
    UUID conversationId,
    String systemPrompt,
    String customerName,
    String customerPhone,
    /** Chronological list (oldest first). The very last entry is the customer's latest message. */
    List<HistoryMessage> history,
    /** Tenant-scoped chunks retrieved via similarity search on the customer's latest message. */
    List<RetrievedChunk> retrievedChunks
) {
    public record HistoryMessage(Role role, String body) {}
    public enum Role { CUSTOMER, BOT, AGENT, SYSTEM }

    public record RetrievedChunk(String content, String documentTitle, double score) {}
}
