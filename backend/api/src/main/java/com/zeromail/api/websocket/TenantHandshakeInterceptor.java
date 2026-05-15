package com.zeromail.api.websocket;

import com.zeromail.core.account.persistence.UserRepository;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class TenantHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantHandshakeInterceptor.class);

    private final UserRepository userRepository;

    public TenantHandshakeInterceptor(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler websocketHandler,
            @NonNull Map<String, Object> attributes) {
        if (!(request.getPrincipal() instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        var user = userRepository.findByGoogleSubject(oidcUser.getSubject()).orElse(null);
        if (user == null) {
            log.warn("event=websocket_handshake_user_not_found tenantId=unresolved");
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        attributes.put(WebSocketSessionAttributes.TENANT_ID, user.getTenantId().toString());
        return true;
    }

    @Override
    public void afterHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler websocketHandler,
            Exception exception) {}
}
