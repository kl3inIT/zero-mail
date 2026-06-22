package com.zeromail.api.security;

import com.zeromail.api.config.ApiProperties;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.account.usecases.OAuthProvisioningService;
import com.zeromail.core.account.usecases.OAuthProvisioningService.BundledProvisioningResult;
import com.zeromail.core.admin.tenant.usecases.TenantActivityRecorder;
import com.zeromail.core.admin.tenant.usecases.TenantActivityRequestContext;
import com.zeromail.core.gmail.exception.DuplicateActiveMailboxException;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.referral.usecases.ReferralCampaignService;
import com.zeromail.core.rules.usecases.RuleTemplateMaterializationService;
import com.zeromail.core.tenant.TenantContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Bundled-scope OAuth2 success handler (Phase 01.5 D-A1, D-A2, D-B2).
 *
 * <p>Replaces the previous two-handler dispatch ({@code OAuth2LoginDispatchingSuccessHandler} →
 * {@code GoogleOAuthSuccessHandler} / {@code GmailOAuthSuccessHandler}) with a single handler that
 * performs all scope validation, null-refresh-token checks, and provisioning delegation in one
 * pass. Deleted two-leg files per D-A3.
 *
 * <p><b>All-or-nothing policy (D-A2):</b>
 *
 * <ol>
 *   <li>If {@code gmail.modify} is absent from granted scopes → throw {@link
 *       OAuth2AuthenticationException}{@code ("gmail_scope_required")} BEFORE any DB write. Failure
 *       handler redirects to {@code /login?error=gmail_scope_required}. (1a) Pre-throw cleanup:
 *       before throwing, {@code removeAuthorizedClient("google", token.getName())} is called so the
 *       {@code LoginRedirectAuthenticationFailureHandler}'s best-effort cleanup is no longer the
 *       sole cleanup path (CR-02 fix).
 *   <li>If refresh token is null AND no existing user → throw {@link
 *       OAuth2AuthenticationException}{@code ("consent_denied")} BEFORE any DB write. Failure
 *       handler redirects to {@code /login?error=consent_denied} (MED-3 fix). Pre-throw cleanup
 *       applied here too (CR-02 fix).
 *   <li>On required Gmail grant → delegate to {@link
 *       OAuthProvisioningService#provisionBundledOAuth} which atomically writes user + tenant +
 *       GmailConnectionEntity in ONE transaction (HIGH-1 fix).
 * </ol>
 *
 * <p><b>Privacy contract (D-E1, T-01.5-01-02, T-01.5-01-03):</b> log statements emit ONLY opaque
 * event names + tenantId UUID — never email, Google subject, or token bytes.
 */
@Component
public class GoogleOAuthSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthSuccessHandler.class);

    private final OAuthProvisioningService provisioningService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final UserRepository userRepository;
    private final GmailConnectionService gmailConnectionService;
    private final RuleTemplateMaterializationService ruleTemplateMaterializationService;
    private final ReferralCampaignService referralCampaignService;
    private final TenantActivityRecorder tenantActivityRecorder;
    private final Clock clock;

    public GoogleOAuthSuccessHandler(
            OAuthProvisioningService provisioningService,
            OAuth2AuthorizedClientService authorizedClientService,
            UserRepository userRepository,
            GmailConnectionService gmailConnectionService,
            RuleTemplateMaterializationService ruleTemplateMaterializationService,
            ReferralCampaignService referralCampaignService,
            TenantActivityRecorder tenantActivityRecorder,
            Clock clock,
            ApiProperties properties) {
        this.provisioningService = provisioningService;
        this.authorizedClientService = authorizedClientService;
        this.userRepository = userRepository;
        this.gmailConnectionService = gmailConnectionService;
        this.ruleTemplateMaterializationService = ruleTemplateMaterializationService;
        this.referralCampaignService =
                Objects.requireNonNull(
                        referralCampaignService, "referralCampaignService must not be null");
        this.tenantActivityRecorder =
                Objects.requireNonNull(
                        tenantActivityRecorder, "tenantActivityRecorder must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");

        // Validate baseUrl scheme/host at construction time so a misconfigured
        // ZEROMAIL_WEB_BASE_URL fails fast instead of silently becoming an open-redirect on
        // the OAuth success path. Same-origin deployments only — http/https schemes; host
        // must be present (no opaque or relative URIs).
        java.net.URI baseUrl = properties.web().baseUrl();
        String scheme = baseUrl.getScheme();
        if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
            throw new IllegalStateException(
                    "zero-mail.api.web.base-url must use http or https scheme; got: " + scheme);
        }
        if (baseUrl.getHost() == null || baseUrl.getHost().isBlank()) {
            throw new IllegalStateException(
                    "zero-mail.api.web.base-url must have a non-blank host");
        }

        // Build the success-redirect URL via UriComponentsBuilder so URI semantics are
        // preserved regardless of input shape.
        String onboardingUrl =
                UriComponentsBuilder.fromUri(baseUrl).path("/onboarding").build().toUriString();
        setDefaultTargetUrl(onboardingUrl);
    }

    @Override
    public void onAuthenticationSuccess(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Authentication authentication)
            throws IOException, ServletException {
        OAuth2AuthenticationToken authenticationToken = (OAuth2AuthenticationToken) authentication;
        OAuthIntentSnapshot callbackIntentSnapshot = consumeCallbackIntentSnapshot(request);
        SecurityContext initiatingSecurityContext = consumeInitiatingSecurityContext(request);
        String oauthIntent = intentOrFirstLogin(callbackIntentSnapshot);
        if (!supportedIntent(oauthIntent)) {
            log.warn("event=oauth_intent_invalid");
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("oauth_intent_invalid", "OAuth intent is not supported", null));
        }
        OidcUser oidcUser =
                Objects.requireNonNull(
                        (OidcUser) authenticationToken.getPrincipal(),
                        "OIDC principal is required");
        String googleSubject =
                Objects.requireNonNull(
                        oidcUser.getClaimAsString(StandardClaimNames.SUB),
                        "OIDC subject is required");
        String email =
                Objects.requireNonNull(
                        oidcUser.getClaimAsString(StandardClaimNames.EMAIL),
                        "OIDC email is required");
        String profileDisplayName = profileDisplayNameOf(oidcUser);
        String profilePictureUrl = profilePictureUrlOf(oidcUser);

        // Load the authorized client once (all checks reuse this single load).
        OAuth2AuthorizedClient authorizedClient =
                authorizedClientService.loadAuthorizedClient(
                        "google", authenticationToken.getName());

        // (a) Granted-scope check BEFORE any DB write.
        if (authorizedClient == null
                || authorizedClient.getAccessToken() == null
                || !authorizedClient
                        .getAccessToken()
                        .getScopes()
                        .contains(OAuthScopes.GMAIL_MODIFY)) {
            // Clean up any partial AuthorizedClient Spring stored before the success handler ran.
            // Use authenticationToken.getName() — always available from OAuth2AuthenticationToken,
            // unlike request.getUserPrincipal() which is null here (principal not committed to
            // SecurityContext yet). Best-effort; never log the principal name (privacy).
            if (authorizedClient != null) {
                try {
                    authorizedClientService.removeAuthorizedClient(
                            "google", authenticationToken.getName());
                } catch (Exception ignored) {
                    // Best-effort — failure to clean up is non-fatal for the error redirect.
                }
            }
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(
                            "gmail_scope_required", "Required Gmail scope was not granted", null));
        }

        // (b) Null refresh-token check.
        if (authorizedClient.getRefreshToken() == null) {
            if (managementIntent(oauthIntent)) {
                OAuthIntentSnapshot managementIntentSnapshot =
                        requiredManagementIntentSnapshot(callbackIntentSnapshot);
                log.info(
                        "event=oauth_no_refresh_token_management_intent tenantId={}",
                        managementIntentSnapshot.initiatingTenantId());
                removeAuthorizedClient(authenticationToken.getName());
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("consent_denied", "Refresh token was not granted", null));
            }
            boolean existingUser = userRepository.findByGoogleSubject(googleSubject).isPresent();
            if (!existingUser) {
                // First-login with no refresh token: Google already had a prior authorization for
                // this client without offline access. Auto-retry with prompt=consent so the user
                // sees the consent screen and Google issues a fresh refresh token — avoids
                // showing a confusing "consent_denied" error for a normal re-auth scenario.
                log.info("event=oauth_no_refresh_token_first_login_retry_consent");
                removeAuthorizedClient(authenticationToken.getName());
                response.sendRedirect("/oauth2/authorization/google?reconnect=true");
                return;
            }
            // Reconnect null: fall through to provisioning (service handles gracefully).
        }

        // (c) Build gmail-scoped scope string for audit storage.
        String gmailScopes =
                authorizedClient.getAccessToken().getScopes().stream()
                        .filter(scope -> scope.startsWith(OAuthScopes.GMAIL_PREFIX))
                        .sorted()
                        .collect(Collectors.joining(" "));

        // (d) Null-safe refresh-token extraction (reconnect path).
        String refreshToken =
                authorizedClient.getRefreshToken() == null
                        ? null
                        : authorizedClient.getRefreshToken().getTokenValue();

        if (OAuthIntentSnapshot.INTENT_ADD_MAILBOX.equals(oauthIntent)) {
            OAuthIntentSnapshot managementIntentSnapshot =
                    requiredManagementIntentSnapshot(callbackIntentSnapshot);
            String managementRefreshToken = requiredManagementRefreshToken(refreshToken);
            UUID tenantId =
                    verifiedManagementTenantId(managementIntentSnapshot, initiatingSecurityContext);
            restoreInitiatingSecurityContext(request, initiatingSecurityContext);
            try {
                TenantContext.runWith(
                        tenantId,
                        () ->
                                gmailConnectionService.addConnection(
                                        tenantId,
                                        email,
                                        gmailScopes,
                                        managementRefreshToken,
                                        profileDisplayName,
                                        profilePictureUrl));
            } catch (DuplicateActiveMailboxException duplicateActiveMailbox) {
                // The selected Google account is already CONNECTED. Translate the domain exception
                // into an
                // AuthenticationException so the OAuth2 filter routes it to the failure handler
                // (friendly
                // /login redirect) instead of letting a raw RuntimeException escape the success
                // handler
                // into a 500 Whitelabel page. The error code distinguishes a same-workspace re-add
                // from a
                // collision with the user's other workspace so the UI can guide them. The completed
                // OAuth
                // grant proves the user controls this address, so revealing the other-workspace
                // case is not
                // a disclosure to a third party. Never log the email (privacy).
                String duplicateErrorCode = duplicateMailboxErrorCode(duplicateActiveMailbox);
                log.info(
                        "event=oauth_add_mailbox_duplicate tenantId={} scope={}",
                        tenantId,
                        duplicateActiveMailbox.scope());
                throw new OAuth2AuthenticationException(
                        new OAuth2Error(
                                duplicateErrorCode, "Gmail mailbox is already connected", null));
            }
            log.info("event=oauth_add_mailbox_connected tenantId={}", tenantId);
            super.onAuthenticationSuccess(request, response, authentication);
            return;
        }

        if (OAuthIntentSnapshot.INTENT_RECONNECT_MAILBOX.equals(oauthIntent)) {
            OAuthIntentSnapshot managementIntentSnapshot =
                    requiredManagementIntentSnapshot(callbackIntentSnapshot);
            String managementRefreshToken = requiredManagementRefreshToken(refreshToken);
            UUID tenantId =
                    verifiedManagementTenantId(managementIntentSnapshot, initiatingSecurityContext);
            UUID targetMailboxId = managementIntentSnapshot.targetMailboxId();
            if (targetMailboxId == null) {
                log.warn("event=oauth_intent_invalid tenantId={}", tenantId);
                throw new OAuth2AuthenticationException(
                        new OAuth2Error(
                                "oauth_intent_invalid", "Reconnect target is required", null));
            }
            restoreInitiatingSecurityContext(request, initiatingSecurityContext);
            try {
                TenantContext.runWith(
                        tenantId,
                        () ->
                                gmailConnectionService.reconnect(
                                        tenantId,
                                        targetMailboxId,
                                        gmailScopes,
                                        managementRefreshToken,
                                        profileDisplayName,
                                        profilePictureUrl));
            } catch (DuplicateActiveMailboxException duplicateActiveMailbox) {
                // Another active mailbox already owns this email (the disconnected target freed the
                // partial-unique slot and an add re-used the address). Route to the failure handler
                // instead of escaping as a 500. Never log the email (privacy).
                String duplicateErrorCode = duplicateMailboxErrorCode(duplicateActiveMailbox);
                log.info(
                        "event=oauth_reconnect_mailbox_duplicate tenantId={} scope={}",
                        tenantId,
                        duplicateActiveMailbox.scope());
                throw new OAuth2AuthenticationException(
                        new OAuth2Error(
                                duplicateErrorCode, "Gmail mailbox is already connected", null));
            }
            log.info("event=oauth_reconnect_mailbox_connected tenantId={}", tenantId);
            super.onAuthenticationSuccess(request, response, authentication);
            return;
        }

        // (e) Single delegation to atomic provisioning service.
        BundledProvisioningResult provisioningResult =
                provisioningService.provisionBundledOAuth(
                        googleSubject,
                        email,
                        refreshToken,
                        gmailScopes,
                        profileDisplayName,
                        profilePictureUrl);

        Instant loginAt = clock.instant();
        TenantActivityRequestContext requestContext = TenantActivityRequestMetadata.from(request);
        recordLoginActivity(provisioningResult, requestContext, loginAt);
        TenantActivitySessionAttributes.storeLogin(
                request.getSession(true), provisioningResult.tenantId(), loginAt);
        qualifyReferralIfPresent(request, response, provisioningResult.tenantId(), loginAt);

        // (f) First-login only: seed the Inbox-Zero-style default rules (enabled) so the new tenant
        // lands on a populated Rules page. Best-effort and post-commit — the materialization
        // service
        // opens its own REQUIRES_NEW tenant-scoped transaction, so a seeding failure must never
        // break the login redirect (the user can still add rules manually). Idempotent per tenant.
        if (provisioningResult.firstLogin()) {
            try {
                ruleTemplateMaterializationService.materializeDefaultRulesEnabled(
                        provisioningResult.tenantId(), ruleLanguageFor(oidcUser));
            } catch (RuntimeException defaultRuleSeedingFailure) {
                log.warn(
                        "event=default_rules_seed_failed tenantId={} failureClass={}",
                        provisioningResult.tenantId(),
                        defaultRuleSeedingFailure.getClass().getSimpleName());
            }
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }

    private void qualifyReferralIfPresent(
            HttpServletRequest request,
            HttpServletResponse response,
            UUID tenantId,
            Instant qualifiedAt) {
        ReferralAttributionCookie.read(request)
                .ifPresent(
                        referralAttribution -> {
                            try {
                                referralCampaignService.qualifyConversion(
                                        referralAttribution.code(),
                                        tenantId,
                                        referralAttribution.attributedAt(),
                                        qualifiedAt);
                            } catch (RuntimeException referralQualificationFailure) {
                                log.warn(
                                        "event=referral_qualification_failed tenantId={} failureClass={}",
                                        tenantId,
                                        referralQualificationFailure.getClass().getSimpleName());
                            } finally {
                                ReferralAttributionCookie.clear(response, request.isSecure());
                            }
                        });
    }

    private OAuthIntentSnapshot consumeCallbackIntentSnapshot(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object callbackIntentValue =
                session.getAttribute(OAuthIntentSnapshot.CALLBACK_INTENT_SESSION_ATTRIBUTE);
        session.removeAttribute(OAuthIntentSnapshot.CALLBACK_INTENT_SESSION_ATTRIBUTE);
        if (callbackIntentValue == null) {
            return null;
        }
        if (callbackIntentValue instanceof OAuthIntentSnapshot callbackIntentSnapshot) {
            return callbackIntentSnapshot;
        }
        log.warn("event=oauth_intent_invalid");
        return null;
    }

    private SecurityContext consumeInitiatingSecurityContext(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object initiatingSecurityContextValue =
                session.getAttribute(
                        OAuthIntentSnapshot.INITIATING_SECURITY_CONTEXT_SESSION_ATTRIBUTE);
        session.removeAttribute(OAuthIntentSnapshot.INITIATING_SECURITY_CONTEXT_SESSION_ATTRIBUTE);
        if (initiatingSecurityContextValue == null) {
            return null;
        }
        if (initiatingSecurityContextValue instanceof SecurityContext securityContext) {
            return securityContext;
        }
        log.warn("event=oauth_initiating_security_context_invalid");
        return null;
    }

    private UUID verifiedManagementTenantId(
            OAuthIntentSnapshot callbackIntentSnapshot, SecurityContext initiatingSecurityContext) {
        UUID initiatingTenantId = callbackIntentSnapshot.initiatingTenantId();
        Optional<UUID> initiatingSessionTenantId =
                initiatingSessionTenantId(initiatingSecurityContext);
        if (initiatingTenantId == null
                || initiatingSessionTenantId.isEmpty()
                || !initiatingTenantId.equals(initiatingSessionTenantId.get())) {
            log.warn("event=oauth_intent_tenant_mismatch tenantId={}", initiatingTenantId);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(
                            "oauth_intent_tenant_mismatch",
                            "OAuth intent tenant did not match the authenticated session",
                            null));
        }
        return initiatingTenantId;
    }

    private Optional<UUID> initiatingSessionTenantId(SecurityContext initiatingSecurityContext) {
        if (initiatingSecurityContext == null) {
            return Optional.empty();
        }
        Authentication initiatingAuthentication = initiatingSecurityContext.getAuthentication();
        if (initiatingAuthentication == null
                || !(initiatingAuthentication.getPrincipal()
                        instanceof OidcUser initiatingOidcUser)) {
            return Optional.empty();
        }
        return userRepository
                .findByGoogleSubject(initiatingOidcUser.getSubject())
                .map(UserEntity::getTenantId);
    }

    private static OAuthIntentSnapshot requiredManagementIntentSnapshot(
            OAuthIntentSnapshot callbackIntentSnapshot) {
        return Objects.requireNonNull(
                callbackIntentSnapshot, "OAuth management intent snapshot is required");
    }

    private static String requiredManagementRefreshToken(String refreshToken) {
        return Objects.requireNonNull(
                refreshToken, "Refresh token is required for mailbox management OAuth intent");
    }

    private static void restoreInitiatingSecurityContext(
            HttpServletRequest request, SecurityContext initiatingSecurityContext) {
        if (initiatingSecurityContext == null) {
            return;
        }
        SecurityContextHolder.setContext(initiatingSecurityContext);
        request.getSession(true)
                .setAttribute(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                        initiatingSecurityContext);
    }

    private void removeAuthorizedClient(String principalName) {
        try {
            authorizedClientService.removeAuthorizedClient("google", principalName);
        } catch (Exception ignored) {
            // Best-effort — failure to clean up is non-fatal for the error redirect.
        }
    }

    private static String intentOrFirstLogin(OAuthIntentSnapshot callbackIntentSnapshot) {
        return callbackIntentSnapshot == null
                ? OAuthIntentSnapshot.INTENT_FIRST_LOGIN
                : callbackIntentSnapshot.intent();
    }

    private static boolean managementIntent(String oauthIntent) {
        return OAuthIntentSnapshot.INTENT_ADD_MAILBOX.equals(oauthIntent)
                || OAuthIntentSnapshot.INTENT_RECONNECT_MAILBOX.equals(oauthIntent);
    }

    private static boolean supportedIntent(String oauthIntent) {
        return OAuthIntentSnapshot.INTENT_FIRST_LOGIN.equals(oauthIntent)
                || OAuthIntentSnapshot.INTENT_ADD_MAILBOX.equals(oauthIntent)
                || OAuthIntentSnapshot.INTENT_RECONNECT_MAILBOX.equals(oauthIntent);
    }

    /**
     * Maps the duplicate scope to the login {@code ?error=} code the failure handler renders. Same
     * workspace → {@code mailbox_already_connected} (you re-added your own mailbox). Other
     * workspace → {@code mailbox_in_other_workspace} (the address has its own Zero Mail tenant; it
     * must be freed there first).
     */
    private static String duplicateMailboxErrorCode(
            DuplicateActiveMailboxException duplicateActiveMailbox) {
        return duplicateActiveMailbox.scope()
                        == DuplicateActiveMailboxException.Scope.OTHER_WORKSPACE
                ? "mailbox_in_other_workspace"
                : "mailbox_already_connected";
    }

    private void recordLoginActivity(
            BundledProvisioningResult provisioningResult,
            TenantActivityRequestContext requestContext,
            Instant loginAt) {
        try {
            tenantActivityRecorder.recordLogin(
                    provisioningResult.tenantId(), requestContext, loginAt);
        } catch (RuntimeException activityRecordingFailure) {
            log.warn(
                    "event=tenant_activity_login_record_failed tenantId={} failureClass={}",
                    provisioningResult.tenantId(),
                    activityRecordingFailure.getClass().getSimpleName());
        }
    }

    /**
     * Picks the language for first-login default-rule seeding from the Google OIDC {@code locale}
     * claim (e.g. {@code "en"}, {@code "en-GB"}, {@code "vi"}). Returns {@code "en"} only when the
     * locale clearly starts with English; everything else (including a missing claim) falls back to
     * {@code "vi"} — the app is Vietnamese-first. Never logs the claim (privacy).
     */
    private static String ruleLanguageFor(OidcUser oidcUser) {
        String locale = oidcUser.getClaimAsString(StandardClaimNames.LOCALE);
        return locale != null && locale.toLowerCase(java.util.Locale.ROOT).startsWith("en")
                ? "en"
                : "vi";
    }

    private static String profileDisplayNameOf(OidcUser oidcUser) {
        String fullName = clean(oidcUser.getFullName());
        if (fullName != null) {
            return fullName;
        }
        return clean(oidcUser.getClaimAsString(StandardClaimNames.NAME));
    }

    private static String profilePictureUrlOf(OidcUser oidcUser) {
        String picture = clean(oidcUser.getPicture());
        if (picture != null) {
            return picture;
        }
        return clean(oidcUser.getClaimAsString(StandardClaimNames.PICTURE));
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
