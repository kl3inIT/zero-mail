package com.zeromail.core.messaging.usecases;

import com.zeromail.core.messaging.domain.MessagingChannel;
import java.util.Objects;
import java.util.UUID;

/** Input contract for resolving a mobile-channel conversation into a Zero Mail chat session. */
public record MessagingConversationResolveCommand(
        UUID tenantId,
        MessagingChannel channel,
        String externalAccountId,
        String externalConversationId,
        String externalUserId,
        UUID mailAccountId,
        String chatTitle) {

    private static final String DEFAULT_CHAT_TITLE = "Messaging";

    public MessagingConversationResolveCommand {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(channel, "channel");
        externalAccountId = requireText(externalAccountId, "externalAccountId");
        externalConversationId = requireText(externalConversationId, "externalConversationId");
        externalUserId = blankToNull(externalUserId);
        chatTitle = normalizeChatTitle(chatTitle);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeChatTitle(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_CHAT_TITLE;
        }
        String trimmedValue = value.trim();
        return trimmedValue.length() <= 200 ? trimmedValue : trimmedValue.substring(0, 200);
    }
}
