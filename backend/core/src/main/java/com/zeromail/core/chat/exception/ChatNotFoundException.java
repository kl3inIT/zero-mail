package com.zeromail.core.chat.exception;

import java.util.UUID;

@SuppressWarnings("unused")
public class ChatNotFoundException extends RuntimeException {

    public ChatNotFoundException(UUID chatId) {
        super("Chat was not found.");
    }
}
