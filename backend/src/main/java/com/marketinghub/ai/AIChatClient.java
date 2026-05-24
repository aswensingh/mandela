package com.marketinghub.ai;

/**
 * Strategy interface: real Spring-AI-backed implementation OR a deterministic mock,
 * picked by Spring config based on the ai.mock property.
 */
public interface AIChatClient {
    BotReply generateReply(AIChatRequest request);
}
