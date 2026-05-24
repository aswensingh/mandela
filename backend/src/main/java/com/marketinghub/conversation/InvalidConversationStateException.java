package com.marketinghub.conversation;

public class InvalidConversationStateException extends RuntimeException {
    public InvalidConversationStateException(String message) {
        super(message);
    }
}
