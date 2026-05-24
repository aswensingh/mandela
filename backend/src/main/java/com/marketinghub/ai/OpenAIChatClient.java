package com.marketinghub.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Real Spring AI -> OpenAI implementation. Only registered when ai.mock=false AND the
 * OpenAI auto-config has produced a ChatModel bean (i.e. OPENAI_API_KEY is set).
 *
 * Uses Spring AI's BeanOutputConverter under the covers (via .entity()) — the JSON
 * schema for {@link BotReply} is injected into the prompt and the response is parsed
 * back into the record.
 */
@Component
@ConditionalOnProperty(name = "ai.mock", havingValue = "false")
public class OpenAIChatClient implements AIChatClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAIChatClient.class);

    private final ChatClient chatClient;

    public OpenAIChatClient(ChatModel chatModel) {
        this.chatClient = ChatClient.create(chatModel);
        log.info("AIChatClient: OpenAI mode active");
    }

    @Override
    public BotReply generateReply(AIChatRequest request) {
        List<Message> history = new ArrayList<>();
        for (AIChatRequest.HistoryMessage m : request.history()) {
            if (m.role() == AIChatRequest.Role.CUSTOMER) {
                history.add(new UserMessage(m.body()));
            } else {
                // BOT / AGENT / SYSTEM all become assistant messages — they're "our" side of the chat.
                history.add(new AssistantMessage(m.body()));
            }
        }
        String customerContext = "Customer phone: " + nullSafe(request.customerPhone())
            + (request.customerName() != null ? "; name: " + request.customerName() : "");
        String ragBlock = ragContextBlock(request);

        StringBuilder system = new StringBuilder(request.systemPrompt())
            .append("\n\n").append(customerContext);
        if (!ragBlock.isBlank()) {
            system.append("\n\n").append(ragBlock);
        }

        return chatClient.prompt()
            .system(s -> s.text(system.toString()))
            .messages(history)
            .call()
            .entity(BotReply.class);
    }

    private static String ragContextBlock(AIChatRequest req) {
        if (req.retrievedChunks() == null || req.retrievedChunks().isEmpty()) return "";
        StringBuilder sb = new StringBuilder("Use the following knowledge base context when relevant. ")
            .append("If the answer is in the context, ground your reply in it.\n");
        int i = 1;
        for (AIChatRequest.RetrievedChunk c : req.retrievedChunks()) {
            sb.append("\n[").append(i++);
            if (c.documentTitle() != null && !c.documentTitle().isBlank()) {
                sb.append(" — ").append(c.documentTitle());
            }
            sb.append("]\n").append(c.content());
        }
        return sb.toString();
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }
}
