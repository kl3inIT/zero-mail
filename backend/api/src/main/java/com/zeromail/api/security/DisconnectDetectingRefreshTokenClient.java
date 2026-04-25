package com.zeromail.api.security;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Component;

import com.zeromail.api.security.events.OAuth2TokenRefreshFailed;
import com.zeromail.core.tenant.TenantContext;

/**
 * Wraps Spring Security 7's RestClientRefreshTokenTokenResponseClient. On Google
 * `invalid_grant` (refresh token revoked or expired), publishes an
 * {@link OAuth2TokenRefreshFailed} event so {@link GmailAccessGuard} can flip the
 * tenant's gmail_connection to DISCONNECTED, then rethrows for normal error flow.
 */
@Component
public class DisconnectDetectingRefreshTokenClient
        implements OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> {

    private final OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> delegate;
    private final ApplicationEventPublisher publisher;

    public DisconnectDetectingRefreshTokenClient(ApplicationEventPublisher publisher) {
        this.delegate = new RestClientRefreshTokenTokenResponseClient();
        this.publisher = publisher;
    }

    @Override
    public OAuth2AccessTokenResponse getTokenResponse(OAuth2RefreshTokenGrantRequest req) {
        try {
            return delegate.getTokenResponse(req);
        } catch (OAuth2AuthorizationException ex) {
            if ("invalid_grant".equals(ex.getError().getErrorCode())) {
                String tenant = TenantContext.currentOptional().orElse("unknown");
                publisher.publishEvent(new OAuth2TokenRefreshFailed(tenant, "invalid_grant", Instant.now()));
            }
            throw ex;
        }
    }
}
