package com.marketinghub.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Deterministic stand-in for OpenAI used in local dev + verification gates.
 *
 * Behaviour:
 *  - If the most recent customer message contains a handoff keyword, return
 *    {@code request_handoff=true} with a high confidence and a "let me get you a human"
 *    line. The worker should NOT send this reply — it should flip to HUMAN_ACTIVE.
 *  - Otherwise, echo a canned acknowledgement that quotes the customer message so the
 *    verification can confirm the round-trip.
 */
@Component
@ConditionalOnProperty(name = "ai.mock", havingValue = "true", matchIfMissing = true)
public class MockAIChatClient implements AIChatClient {

    private static final Logger log = LoggerFactory.getLogger(MockAIChatClient.class);

    private static final List<String> HANDOFF_KEYWORDS = List.of(
        "human", "agent", "real person", "speak to someone", "talk to a person",
        "talk to someone", "customer service", "manager"
    );

    public MockAIChatClient() {
        log.info("AIChatClient: MOCK mode active — replies are canned, OpenAI is NOT called");
    }

    @Override
    public BotReply generateReply(AIChatRequest request) {
        String lastUser = lastCustomerMessage(request);
        boolean handoff = lastUser != null && containsHandoffKeyword(lastUser);
        if (handoff) {
            return new BotReply(
                "Let me connect you with a human teammate — one moment.",
                0.95,
                true
            );
        }
        // If RAG produced a chunk, surface its text verbatim so the verification gate
        // can grep for doc content (e.g., "teeth whitening at $150"). Real OpenAI in
        // non-mock mode does this naturally by reasoning over the context block.
        String topChunk = topChunkText(request);
        String preview = lastUser == null ? "your message" : truncate(lastUser, 80);
        String reply;
        if (topChunk != null && !topChunk.isBlank()) {
            reply = "From our knowledge base: \""
                + truncate(topChunk, 600)
                + "\" — let me know if that helps with \"" + preview + "\".";
        } else {
            reply = "Thanks for reaching out! I read: \"" + preview + "\". A teammate will follow up soon.";
        }
        return new BotReply(reply, 0.85, false);
    }

    private static String topChunkText(AIChatRequest req) {
        if (req.retrievedChunks() == null || req.retrievedChunks().isEmpty()) return null;
        return req.retrievedChunks().get(0).content();
    }

    private static String lastCustomerMessage(AIChatRequest req) {
        for (int i = req.history().size() - 1; i >= 0; i--) {
            AIChatRequest.HistoryMessage m = req.history().get(i);
            if (m.role() == AIChatRequest.Role.CUSTOMER) {
                return m.body();
            }
        }
        return null;
    }

    private static boolean containsHandoffKeyword(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String kw : HANDOFF_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
