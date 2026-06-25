package com.zeromail.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * Single-registration authorization-request resolver for the bundled {@code google} OAuth2 client
 * (Phase 01.5 D-A1, D-A5). Replaces the two-registration {@code GmailScopeRequestResolver} (deleted
 * in Phase 01.5 D-A3).
 *
 * <p>Runtime signal: when the request carries a {@code ?reconnect=true} query parameter, {@code
 * prompt=consent} is added to force Google to re-show the consent screen and re-issue a refresh
 * token. This query parameter carries no branch authority; add/reconnect routing is read only from
 * {@link OAuthIntentSnapshot#PENDING_INTENT_SESSION_ATTRIBUTE}, which is written server-side by the
 * authenticated mailbox-management endpoint and re-checked on callback.
 *
 * <p>{@code access_type=offline} and {@code include_granted_scopes=true} are always set: {@code
 * offline} guarantees refresh-token issuance; {@code include_granted_scopes} ensures the bundled
 * scope list is returned even if the user already had some scopes granted.
 *
 * <p>Login-hint injection removed: the Phase 01.4 two-leg same-account guarantee relied on {@code
 * login_hint}; the bundled flow makes it unnecessary (D-A3 collapse eliminates the mismatch surface
 * entirely).
 *
 * <p><b>Security note (T-01.5-01-01):</b> the {@code ?reconnect=true} signal is untrusted user
 * input. Worst-case: an attacker adds it to the first-time login URL and the user sees {@code
 * prompt=consent} (extra friction, not a security regression — no persistence side effect from the
 * resolver itself).
 */
@Component
public class GoogleAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private static final Logger log =
            LoggerFactory.getLogger(GoogleAuthorizationRequestResolver.class);

    private static final String RECONNECT_PARAMETER = "reconnect";

    private final DefaultOAuth2AuthorizationRequestResolver defaultAuthorizationRequestResolver;
    private final ReferralAttributionTokenCodec referralAttributionTokenCodec;

    public GoogleAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository,
            ReferralAttributionTokenCodec referralAttributionTokenCodec) {
        this.defaultAuthorizationRequestResolver =
                new DefaultOAuth2AuthorizationRequestResolver(
                        clientRegistrationRepository, "/oauth2/authorization");
        this.referralAttributionTokenCodec =
                Objects.requireNonNull(
                        referralAttributionTokenCodec,
                        "referralAttributionTokenCodec must not be null");
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest servletRequest) {
        return customizeAuthorizationRequest(
                defaultAuthorizationRequestResolver.resolve(servletRequest), servletRequest);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(
            HttpServletRequest servletRequest, String clientRegistrationId) {
        return customizeAuthorizationRequest(
                defaultAuthorizationRequestResolver.resolve(servletRequest, clientRegistrationId),
                servletRequest);
    }

    private OAuth2AuthorizationRequest customizeAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest servletRequest) {
        if (authorizationRequest == null) return null;
        OAuthIntentSnapshot pendingIntentSnapshot = consumePendingIntentSnapshot(servletRequest);
        ReferralAttributionSnapshot referralAttributionSnapshot =
                referralAttributionTokenCodec
                        .decode(
                                servletRequest.getParameter(
                                        ReferralAttributionSnapshot.QUERY_PARAMETER))
                        .orElse(null);
        var additionalParameters = new HashMap<>(authorizationRequest.getAdditionalParameters());
        additionalParameters.put("access_type", "offline");
        additionalParameters.put("include_granted_scopes", "true");
        // D-A5: prompt=consent only on reconnect path (forces re-consent + new refresh token).
        // First-time login: NO prompt — smooth UX, refresh token issued on first offline grant
        // anyway.
        if ("true".equals(servletRequest.getParameter(RECONNECT_PARAMETER))) {
            additionalParameters.put("prompt", "consent");
        }
        var authorizationRequestBuilder =
                OAuth2AuthorizationRequest.from(authorizationRequest)
                        .additionalParameters(Map.copyOf(additionalParameters))
                        .attributes(
                                attributes -> {
                                    if (pendingIntentSnapshot != null) {
                                        attributes.put(
                                                OAuthIntentSnapshot.ATTRIBUTE_INTENT,
                                                pendingIntentSnapshot.intent());
                                        attributes.put(
                                                OAuthIntentSnapshot.ATTRIBUTE_TARGET_MAILBOX_ID,
                                                pendingIntentSnapshot.targetMailboxId());
                                        attributes.put(
                                                OAuthIntentSnapshot.ATTRIBUTE_INITIATING_TENANT_ID,
                                                pendingIntentSnapshot.initiatingTenantId());
                                    }
                                    if (referralAttributionSnapshot != null) {
                                        attributes.put(
                                                ReferralAttributionSnapshot.ATTRIBUTE_CODE,
                                                referralAttributionSnapshot.code());
                                        attributes.put(
                                                ReferralAttributionSnapshot
                                                        .ATTRIBUTE_ATTRIBUTED_AT_EPOCH_MILLIS,
                                                referralAttributionSnapshot
                                                        .attributedAt()
                                                        .toEpochMilli());
                                    }
                                });
        return authorizationRequestBuilder.build();
    }

    private OAuthIntentSnapshot consumePendingIntentSnapshot(HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(false);
        if (session == null) {
            return null;
        }
        Object pendingIntentValue =
                session.getAttribute(OAuthIntentSnapshot.PENDING_INTENT_SESSION_ATTRIBUTE);
        if (pendingIntentValue == null) {
            return null;
        }
        session.removeAttribute(OAuthIntentSnapshot.PENDING_INTENT_SESSION_ATTRIBUTE);
        if (!(pendingIntentValue instanceof OAuthIntentSnapshot pendingIntentSnapshot)) {
            log.warn("event=oauth_pending_intent_invalid");
            return null;
        }
        if (!validManagementIntent(pendingIntentSnapshot)) {
            log.warn("event=oauth_pending_intent_invalid");
            return null;
        }
        return pendingIntentSnapshot;
    }

    private static boolean validManagementIntent(OAuthIntentSnapshot pendingIntentSnapshot) {
        if (pendingIntentSnapshot.initiatingTenantId() == null) {
            return false;
        }
        return switch (pendingIntentSnapshot.intent()) {
            case OAuthIntentSnapshot.INTENT_ADD_MAILBOX -> true;
            case OAuthIntentSnapshot.INTENT_RECONNECT_MAILBOX ->
                    pendingIntentSnapshot.targetMailboxId() != null;
            default -> false;
        };
    }
}
