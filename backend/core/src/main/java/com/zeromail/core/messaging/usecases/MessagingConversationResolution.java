package com.zeromail.core.messaging.usecases;

import com.zeromail.core.messaging.domain.MessagingChannel;
import java.util.UUID;

/** Result of binding an external channel conversation to the active internal chat session. */
public record MessagingConversationResolution(
        UUID tenantId,
        MessagingChannel channel,
        UUID channelAccountId,
        UUID conversationId,
        UUID conversationSessionId,
        UUID chatId,
        UUID mailAccountId,
        boolean createdConversation,
        boolean createdSession) {}
