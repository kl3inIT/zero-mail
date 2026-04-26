package com.zeromail.api.security;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * Typed exception thrown by {@code GmailOAuthSuccessHandler} when the OIDC subject
 * returned by the leg-2 Gmail OAuth callback does not match the currently signed-in
 * user's stored {@code googleSubject}. Extends {@link OAuth2AuthenticationException}
 * so Spring Security routes it through the configured {@code failureHandler} chain
 * (a non-OAuth {@link RuntimeException} would bypass and trigger a generic 500
 * instead of the controlled {@code /onboarding?error=gmail_identity_mismatch}
 * redirect).
 *
 * <p>The leg-2 access-token value is captured here so the failure handler can
 * revoke it without re-loading the {@code OAuth2AuthorizedClient} (which the
 * failure handler removes per Pitfall 2 — orphan-avoidance).
 *
 * <p><b>Privacy contract (D-E1):</b> the {@link OAuth2Error} description and the
 * exception message MUST NOT contain user email, Google subject, or token bytes —
 * exception messages flow through logs and any of those identifiers would violate
 * threat T-1.4-02-token-leak.
 */
public class GmailIdentityMismatchException extends OAuth2AuthenticationException {

    private final String accessTokenToRevoke;

    public GmailIdentityMismatchException(String accessTokenToRevoke) {
        super(new OAuth2Error(
                "gmail_identity_mismatch",
                "Gmail OIDC subject does not match logged-in user",
                null));
        this.accessTokenToRevoke = accessTokenToRevoke;
    }

    public String getAccessTokenToRevoke() {
        return accessTokenToRevoke;
    }
}
