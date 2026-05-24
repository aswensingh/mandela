package com.marketinghub.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Structured output the LLM is asked to return. We feed this class to Spring AI's
 * BeanOutputConverter so the JSON schema lands in the prompt and the LLM is constrained
 * to match it.
 */
@JsonPropertyOrder({"reply", "confidence", "request_handoff"})
public record BotReply(
    @JsonProperty(required = true, value = "reply") String reply,
    @JsonProperty(required = true, value = "confidence") double confidence,
    @JsonProperty(required = true, value = "request_handoff") boolean requestHandoff
) {}
