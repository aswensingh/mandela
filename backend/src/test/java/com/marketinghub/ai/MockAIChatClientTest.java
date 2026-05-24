package com.marketinghub.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MockAIChatClientTest {

    private final MockAIChatClient client = new MockAIChatClient();

    @Test
    void emitsHandoffWhenCustomerAsksForHuman() {
        BotReply r = client.generateReply(req("I want to talk to a human", List.of()));
        assertThat(r.requestHandoff()).isTrue();
        assertThat(r.confidence()).isGreaterThanOrEqualTo(0.9);
    }

    @Test
    void emitsHandoffOnAgentKeyword() {
        BotReply r = client.generateReply(req("can I speak to an agent please", List.of()));
        assertThat(r.requestHandoff()).isTrue();
    }

    @Test
    void normalQuestion_repliesWithoutHandoff() {
        BotReply r = client.generateReply(req("when do you open on Saturdays?", List.of()));
        assertThat(r.requestHandoff()).isFalse();
        assertThat(r.reply()).contains("Saturdays");
    }

    @Test
    void quotesTopRetrievedChunkInReply() {
        List<AIChatRequest.RetrievedChunk> chunks = List.of(
            new AIChatRequest.RetrievedChunk(
                "Our clinic offers teeth whitening at $150.", "FAQ", 0.0));
        BotReply r = client.generateReply(req("how much for whitening?", chunks));
        assertThat(r.requestHandoff()).isFalse();
        assertThat(r.reply()).contains("teeth whitening at $150");
    }

    @Test
    void noRetrievedChunks_fallsBackToCannedReply() {
        BotReply r = client.generateReply(req("anything", List.of()));
        assertThat(r.reply()).doesNotContain("From our knowledge base");
    }

    private static AIChatRequest req(String customerLine, List<AIChatRequest.RetrievedChunk> chunks) {
        return new AIChatRequest(
            UUID.randomUUID(), UUID.randomUUID(),
            "system prompt", "Customer", "+14155557777",
            List.of(new AIChatRequest.HistoryMessage(AIChatRequest.Role.CUSTOMER, customerLine)),
            chunks
        );
    }
}
