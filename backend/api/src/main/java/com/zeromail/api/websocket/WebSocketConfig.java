package com.zeromail.api.websocket;

import com.zeromail.api.config.ApiProperties;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ApiProperties properties;
    private final TenantHandshakeInterceptor tenantHandshakeInterceptor;
    private final TenantSubscriptionInterceptor tenantSubscriptionInterceptor;

    public WebSocketConfig(
            ApiProperties properties,
            TenantHandshakeInterceptor tenantHandshakeInterceptor,
            TenantSubscriptionInterceptor tenantSubscriptionInterceptor) {
        this.properties = properties;
        this.tenantHandshakeInterceptor = tenantHandshakeInterceptor;
        this.tenantSubscriptionInterceptor = tenantSubscriptionInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        StompWebSocketEndpointRegistration endpointRegistration =
                registry.addEndpoint("/ws").addInterceptors(tenantHandshakeInterceptor);
        List<String> allowedOriginPatterns = properties.cors().allowedOriginPatterns();
        if (!allowedOriginPatterns.isEmpty()) {
            endpointRegistration.setAllowedOriginPatterns(
                    allowedOriginPatterns.toArray(String[]::new));
            return;
        }
        endpointRegistration.setAllowedOrigins(
                properties.cors().allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(tenantSubscriptionInterceptor);
    }
}
