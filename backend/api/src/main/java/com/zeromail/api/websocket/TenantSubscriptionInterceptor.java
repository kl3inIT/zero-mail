package com.zeromail.api.websocket;

import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class TenantSubscriptionInterceptor implements ChannelInterceptor {

    private static final String BILLING_TOPIC_PREFIX = "/topic/tenants/";
    private static final String BILLING_TOPIC_SUFFIX = "/billing";

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        String destination = accessor.getDestination();
        if (!isTenantBillingTopic(destination)) {
            return message;
        }

        String requestedTenantId = extractTenantId(destination);
        String sessionTenantId = sessionTenantId(accessor.getSessionAttributes());
        if (!requestedTenantId.equals(sessionTenantId)) {
            throw new AccessDeniedException("Cannot subscribe to another tenant billing topic");
        }
        return message;
    }

    private static boolean isTenantBillingTopic(String destination) {
        return destination != null
                && destination.startsWith(BILLING_TOPIC_PREFIX)
                && destination.endsWith(BILLING_TOPIC_SUFFIX)
                && destination.length()
                        > BILLING_TOPIC_PREFIX.length() + BILLING_TOPIC_SUFFIX.length();
    }

    private static String extractTenantId(String destination) {
        return destination.substring(
                BILLING_TOPIC_PREFIX.length(),
                destination.length() - BILLING_TOPIC_SUFFIX.length());
    }

    private static String sessionTenantId(Map<String, Object> sessionAttributes) {
        if (sessionAttributes == null) {
            throw new AccessDeniedException("Missing websocket tenant session");
        }
        Object tenantId = sessionAttributes.get(WebSocketSessionAttributes.TENANT_ID);
        if (!(tenantId instanceof String tenantIdString) || tenantIdString.isBlank()) {
            throw new AccessDeniedException("Missing websocket tenant session");
        }
        return tenantIdString;
    }
}
