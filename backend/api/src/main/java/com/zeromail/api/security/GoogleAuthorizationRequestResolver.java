package com.zeromail.api.security;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Single-registration authorization-request resolver for the bundled {@code google} OAuth2
 * client (Phase 01.5 D-A1, D-A5). Replaces the two-registration {@link GmailScopeRequestResolver}
 * (deleted in Phase 01.5 D-A3).
 *
 * <p>Runtime signal: when the request carries a {@code ?reconnect=true} query parameter
 * (set by {@code ConnectGmailController}'s redirect), {@code prompt=consent} is added to
 * force Google to re-show the consent screen and re-issue a refresh token. All other
 * requests (first-time login from {@code /login}) omit {@code prompt} entirely for smooth
 * UX (D-A5 decision).
 *
 * <p>{@code access_type=offline} and {@code include_granted_scopes=true} are always set:
 * {@code offline} guarantees refresh-token issuance; {@code include_granted_scopes} ensures
 * the bundled scope list is returned even if the user already had some scopes granted.
 *
 * <p>Login-hint injection removed: the Phase 01.4 two-leg same-account guarantee relied on
 * {@code login_hint}; the bundled flow makes it unnecessary (D-A3 collapse eliminates
 * the mismatch surface entirely).
 *
 * <p><b>Security note (T-01.5-01-01):</b> the {@code ?reconnect=true} signal is untrusted user
 * input. Worst-case: an attacker adds it to the first-time login URL and the user sees
 * {@code prompt=consent} (extra friction, not a security regression — no persistence
 * side effect from the resolver itself).
 */
@Component
public class GoogleAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private static final String GOOGLE_REGISTRATION_ID = "google";
    private static final String RECONNECT_PARAM = "reconnect";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public GoogleAuthorizationRequestResolver(ClientRegistrationRepository repo) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest req) {
        return customize(delegate.resolve(req), req);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest req, String clientRegistrationId) {
        return customize(delegate.resolve(req, clientRegistrationId), req);
    }

    private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest r, HttpServletRequest req) {
        if (r == null) return null;
        var extra = new HashMap<>(r.getAdditionalParameters());
        extra.put("access_type", "offline");
        extra.put("include_granted_scopes", "true");
        // D-A5: prompt=consent only on reconnect path (forces re-consent + new refresh token).
        // First-time login: NO prompt — smooth UX, refresh token issued on first offline grant anyway.
        if ("true".equals(req.getParameter(RECONNECT_PARAM))) {
            extra.put("prompt", "consent");
        }
        return OAuth2AuthorizationRequest.from(r).additionalParameters(Map.copyOf(extra)).build();
    }
}
