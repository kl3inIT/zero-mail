package com.zeromail.api.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

class TenantSubscriptionInterceptorTest {

    private final TenantSubscriptionInterceptor interceptor = new TenantSubscriptionInterceptor();
    private final MessageChannel channel = mock(MessageChannel.class);

    @Test
    void allows_billing_subscription_for_session_tenant() {
        UUID tenantId = UUID.randomUUID();
        Message<byte[]> message =
                subscribeMessage("/topic/tenants/" + tenantId + "/billing", tenantId.toString());

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
    }

    @Test
    void rejects_billing_subscription_for_other_tenant() {
        UUID sessionTenantId = UUID.randomUUID();
        UUID requestedTenantId = UUID.randomUUID();
        Message<byte[]> message =
                subscribeMessage(
                        "/topic/tenants/" + requestedTenantId + "/billing",
                        sessionTenantId.toString());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ignores_non_billing_subscription() {
        Message<byte[]> message =
                subscribeMessage("/topic/system/health", UUID.randomUUID().toString());

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
    }

    private static Message<byte[]> subscribeMessage(String destination, String sessionTenantId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put(WebSocketSessionAttributes.TENANT_ID, sessionTenantId);
        accessor.setSessionAttributes(sessionAttributes);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
