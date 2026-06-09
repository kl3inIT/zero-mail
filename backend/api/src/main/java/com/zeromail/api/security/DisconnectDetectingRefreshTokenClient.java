package com.zeromail.api.security;

import com.zeromail.api.security.events.OAuth2TokenRefreshFailed;
import com.zeromail.core.mailbox.MailboxContext;
import com.zeromail.core.tenant.TenantContext;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Component;

/**
 * Wraps Spring Security 7's RestClientRefreshTokenTokenResponseClient. On Google `invalid_grant`
 * (refresh token revoked or expired), publishes an {@link OAuth2TokenRefreshFailed} event so {@link
 * GmailAccessGuard} can flip the tenant's gmail_connection to DISCONNECTED, then rethrows for
 * normal error flow.
 */
@Component
public class DisconnectDetectingRefreshTokenClient
        implements OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> {

    private final OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest>
            refreshTokenClient;
    private final ApplicationEventPublisher eventPublisher;

    public DisconnectDetectingRefreshTokenClient(ApplicationEventPublisher eventPublisher) {
        this.refreshTokenClient = new RestClientRefreshTokenTokenResponseClient();
        this.eventPublisher = eventPublisher;
    }

    @Override
    public OAuth2AccessTokenResponse getTokenResponse(
            OAuth2RefreshTokenGrantRequest refreshTokenGrantRequest) {
        try {
            return refreshTokenClient.getTokenResponse(refreshTokenGrantRequest);
        } catch (OAuth2AuthorizationException authorizationException) {
            if ("invalid_grant".equals(authorizationException.getError().getErrorCode())) {
                String tenantId = TenantContext.currentOptional().orElse("unknown");
                String gmailConnectionId =
                        MailboxContext.currentOptional().map(Object::toString).orElse(null);
                eventPublisher.publishEvent(
                        new OAuth2TokenRefreshFailed(
                                tenantId, gmailConnectionId, "invalid_grant", Instant.now()));
            }
            throw authorizationException;
        }
    }
}
