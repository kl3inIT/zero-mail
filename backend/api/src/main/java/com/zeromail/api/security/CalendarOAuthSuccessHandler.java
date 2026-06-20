package com.zeromail.api.security;

import com.zeromail.api.config.ApiProperties;
import com.zeromail.core.calendar.domain.CalendarConnectionStatus;
import com.zeromail.core.calendar.persistence.CalendarConnectionEntity;
import com.zeromail.core.calendar.persistence.CalendarConnectionRepository;
import com.zeromail.core.oauth.token.OAuthTokenStore;
import com.zeromail.core.oauth.token.OAuthTokenStore.RowDiscriminator;
import com.zeromail.core.tenant.TenantContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * OAuth2 success handler for the {@code google-calendar} registration (Phase 12 W1, CAL-CONN-01 /
 * CAL-CONN-03). Dispatched by {@link GoogleOAuthSuccessHandler} when {@code
 * authenticationToken.getAuthorizedClientRegistrationId().equals("google-calendar")} — registered
 * here as a {@code @Component} rather than wired into {@code SecurityConfig.oauth2Login()} so the
 * existing single-success-handler topology stays unchanged.
 *
 * <p><b>Invariants (Pitfall 2 mitigation):</b>
 *
 * <ul>
 *   <li>Writes ONLY to {@code calendar_connections.refresh_token_encrypted} via {@code
 *       OAuthTokenStore.encrypt(..., RowDiscriminator.CALENDAR_CONNECTION)}. Never touches {@code
 *       gmail_connections}. Verified by {@code CalendarOAuthTokenIsolationTest}.
 *   <li>Refresh-token bytes never leave the local variable (no log statement references the
 *       refresh-token variable).
 *   <li>{@code googleEmail} is read from the {@code TenantContext}-bound user's session — not from
 *       a new OIDC ID token, because the Calendar flow does NOT request {@code openid}/{@code
 *       profile}/{@code email} scopes.
 * </ul>
 *
 * <p><b>Refresh-token nullability:</b> the resolver always forces {@code prompt=consent} for the
 * calendar flow so Google must re-issue a refresh token. A null refresh token here means the user
 * revoked midway; surface as {@code OAuth2AuthenticationException("consent_denied_calendar")} and
 * leave no DB write.
 *
 * <p>Sub-calendar enumeration via {@code calendarList.list()} is W2 work — this handler ends after
 * persisting exactly one row in {@code calendar_connections}.
 */
@Component
public class CalendarOAuthSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(CalendarOAuthSuccessHandler.class);

    static final String CALENDAR_REGISTRATION_ID = "google-calendar";

    private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;
    private final CalendarConnectionRepository calendarConnectionRepository;
    private final OAuthTokenStore oAuthTokenStore;
    private final ApplicationEventPublisher applicationEventPublisher;

    public CalendarOAuthSuccessHandler(
            OAuth2AuthorizedClientService oAuth2AuthorizedClientService,
            CalendarConnectionRepository calendarConnectionRepository,
            OAuthTokenStore oAuthTokenStore,
            ApplicationEventPublisher applicationEventPublisher,
            ApiProperties apiProperties) {
        this.oAuth2AuthorizedClientService =
                Objects.requireNonNull(
                        oAuth2AuthorizedClientService, "oAuth2AuthorizedClientService");
        this.calendarConnectionRepository =
                Objects.requireNonNull(
                        calendarConnectionRepository, "calendarConnectionRepository");
        this.oAuthTokenStore = Objects.requireNonNull(oAuthTokenStore, "oAuthTokenStore");
        this.applicationEventPublisher =
                Objects.requireNonNull(applicationEventPublisher, "applicationEventPublisher");

        // Redirect target: the W3 settings page. baseUrl scheme/host already validated by
        // GoogleOAuthSuccessHandler at boot; reuse the same ApiProperties value.
        URI baseUrl = apiProperties.web().baseUrl();
        String calendarSettingsUrl =
                UriComponentsBuilder.fromUri(baseUrl)
                        .path("/settings/calendar")
                        .build()
                        .toUriString();
        setDefaultTargetUrl(calendarSettingsUrl);
    }

    @Override
    public void onAuthenticationSuccess(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Authentication authentication)
            throws IOException, ServletException {
        OAuth2AuthenticationToken authenticationToken = (OAuth2AuthenticationToken) authentication;
        if (!CALENDAR_REGISTRATION_ID.equals(
                authenticationToken.getAuthorizedClientRegistrationId())) {
            // Defensive — the dispatcher in GoogleOAuthSuccessHandler should have routed only
            // calendar tokens here, but a misconfiguration would be safer to fail loud than to
            // silently corrupt a Gmail row.
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(
                            "oauth_registration_mismatch",
                            "CalendarOAuthSuccessHandler invoked with non-calendar"
                                    + " registrationId",
                            null));
        }

        OAuth2AuthorizedClient oAuth2AuthorizedClient =
                oAuth2AuthorizedClientService.loadAuthorizedClient(
                        CALENDAR_REGISTRATION_ID, authenticationToken.getName());
        if (oAuth2AuthorizedClient == null || oAuth2AuthorizedClient.getAccessToken() == null) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(
                            "calendar_grant_missing",
                            "Calendar OAuth grant was not stored by Spring Security",
                            null));
        }
        if (oAuth2AuthorizedClient.getRefreshToken() == null) {
            // prompt=consent was forced — null here means the user revoked midway. Best-effort
            // cleanup so the partial AuthorizedClient does not linger.
            removeAuthorizedClient(authenticationToken.getName());
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(
                            "consent_denied_calendar",
                            "Calendar refresh token was not granted",
                            null));
        }

        UUID tenantId = TenantContext.currentTenantUuid();
        // The user's pre-existing OIDC session already provides their identity; the calendar
        // grant carries a separate googleEmail (multi-account from day one per D-05). Pull it
        // from the access-token's "scope" channel via the OAuth2AuthorizedClient's userinfo
        // claim if available; otherwise fall back to the principal name (which is the OIDC
        // subject of the active session).
        String googleEmail = resolveGoogleEmail(oAuth2AuthorizedClient, authenticationToken);
        String refreshTokenValue = oAuth2AuthorizedClient.getRefreshToken().getTokenValue();
        String scopesGranted =
                String.join(" ", oAuth2AuthorizedClient.getAccessToken().getScopes());

        Optional<CalendarConnectionEntity> existingActive =
                calendarConnectionRepository.findActiveByTenantIdAndGoogleEmail(
                        tenantId, googleEmail);
        try {
            if (existingActive.isPresent()) {
                // Re-grant of the same email — refresh the row in place so the partial-unique
                // index never trips.
                CalendarConnectionEntity reGranted = existingActive.get();
                reGranted.setRefreshTokenEncrypted(
                        oAuthTokenStore.encrypt(
                                refreshTokenValue.getBytes(StandardCharsets.UTF_8),
                                tenantId,
                                RowDiscriminator.CALENDAR_CONNECTION));
                reGranted.setScopesGranted(scopesGranted);
                reGranted.setConnectedAt(Instant.now());
                calendarConnectionRepository.save(reGranted);
                log.info(
                        "event=calendar_oauth_regrant_success tenantId={} calendarConnectionId={}",
                        tenantId,
                        reGranted.getId());
            } else {
                CalendarConnectionEntity freshRow =
                        new CalendarConnectionEntity(
                                UUID.randomUUID(),
                                tenantId,
                                googleEmail,
                                CalendarConnectionStatus.CONNECTED);
                freshRow.setRefreshTokenEncrypted(
                        oAuthTokenStore.encrypt(
                                refreshTokenValue.getBytes(StandardCharsets.UTF_8),
                                tenantId,
                                RowDiscriminator.CALENDAR_CONNECTION));
                freshRow.setScopesGranted(scopesGranted);
                freshRow.setConnectedAt(Instant.now());
                calendarConnectionRepository.save(freshRow);
                log.info(
                        "event=calendar_oauth_connect_success tenantId={} calendarConnectionId={}",
                        tenantId,
                        freshRow.getId());
            }
        } catch (DataIntegrityViolationException constraintRace) {
            // A concurrent flow landed the row first — surface as a friendly OAuth error so the
            // user sees a re-grant message instead of a 500. Never log googleEmail.
            log.warn("event=calendar_oauth_duplicate_active tenantId={}", tenantId);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(
                            "calendar_connection_already_active",
                            "Calendar connection for this account is already active",
                            null));
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }

    private void removeAuthorizedClient(String principalName) {
        try {
            oAuth2AuthorizedClientService.removeAuthorizedClient(
                    CALENDAR_REGISTRATION_ID, principalName);
        } catch (Exception ignored) {
            // Best-effort — failure to clean up is non-fatal for the error redirect.
        }
    }

    /**
     * Resolves the {@code googleEmail} the calendar grant belongs to. The OIDC profile claims are
     * not necessarily present on a Calendar-only grant; fall back to the access-token attribute
     * map's {@code email} field if Spring populated one, else to the authentication token's {@code
     * name} (the user's existing session subject).
     */
    private String resolveGoogleEmail(
            OAuth2AuthorizedClient oAuth2AuthorizedClient,
            OAuth2AuthenticationToken authenticationToken) {
        Object emailAttribute = authenticationToken.getPrincipal().getAttribute("email");
        if (emailAttribute instanceof String emailString && !emailString.isBlank()) {
            return emailString;
        }
        return authenticationToken.getName();
    }
}
