package com.zeromail.core.account.usecases;

import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.notification.domain.ChannelType;
import com.zeromail.core.notification.usecases.NotificationPreferenceService;
import com.zeromail.core.onboarding.domain.OnboardingStep;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.usecases.TenantService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Atomic provisioning for the OAuth first-login flow.
 *
 * <p>{@link #provisionBundledOAuth} collapses user + tenant + GmailConnectionEntity creation into
 * ONE TransactionTemplate (PROPAGATION_REQUIRED) so any failure rolls back all three — no
 * half-provisioned state. {@link #provisionBundledOAuth} is the single provisioning surface.
 *
 * <p>ScopedValue binding invariant: {@link TenantContext#TENANT} MUST be bound BEFORE the
 * TransactionTemplate opens (Hibernate captures tenant on JPA session open).
 *
 * <p><b>API surface:</b> accepts plain {@code String} subject/email rather than {@code OidcUser} so
 * this module does not need to depend on Spring Security's OAuth2 module (preserves {@code
 * allowedDependencies} boundary in {@code core.account}).
 */
@Service
public class OAuthProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(OAuthProvisioningService.class);

    private final UserRepository userRepository;
    private final TenantService tenantService;
    private final GmailConnectionService gmailConnectionService;
    private final NotificationPreferenceService notificationPreferenceService;
    private final RefreshTokenCipher refreshTokenCipher;
    private final TransactionTemplate bundledTransaction;

    public OAuthProvisioningService(
            UserRepository userRepository,
            TenantService tenantService,
            GmailConnectionService gmailConnectionService,
            NotificationPreferenceService notificationPreferenceService,
            RefreshTokenCipher refreshTokenCipher,
            PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.tenantService = tenantService;
        this.gmailConnectionService = gmailConnectionService;
        this.notificationPreferenceService = notificationPreferenceService;
        this.refreshTokenCipher = refreshTokenCipher;
        this.bundledTransaction = new TransactionTemplate(transactionManager);
        this.bundledTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    /** Result record for {@link #provisionBundledOAuth}. */
    public record BundledProvisioningResult(UUID tenantId, UUID userId, boolean firstLogin) {}

    /**
     * Atomic bundled provisioning: creates user + tenant + GmailConnectionEntity + advances
     * onboarding step inside ONE {@code PROPAGATION_REQUIRED} transaction. Any failure in any step
     * causes a full rollback — no half-provisioned rows survive.
     *
     * <p>Null {@code refreshTokenPlaintext} handling:
     *
     * <ul>
     *   <li>Existing user (reconnect path): preserves existing GmailConnectionEntity envelope
     *       unchanged; emits opaque warning log {@code event=oauth_no_refresh_token}.
     *   <li>No existing user (first-login path): throws {@code
     *       IllegalStateException("consent_denied")} — caller ({@code GoogleOAuthSuccessHandler})
     *       must have validated this before calling (this is a defensive check; the handler throws
     *       {@code OAuth2AuthenticationException} before reaching here).
     * </ul>
     *
     * <p>Race-loser path ({@code DataIntegrityViolationException} on {@code users.save}): observes
     * the winner's already-complete state, returns {@code BundledProvisioningResult(winner tenant,
     * winner id, false)} without any further DB write — preserving the single-transaction
     * guarantee.
     *
     * <p><b>Privacy:</b> raw refresh-token bytes are encrypted inside the same transaction boundary
     * and NEVER logged. Event names are opaque.
     *
     * @param googleSubject Google OIDC {@code sub} claim
     * @param email Google OIDC {@code email} claim
     * @param refreshTokenPlaintext raw refresh-token string; may be {@code null} on reconnect
     * @param grantedGmailScopes space-joined sorted gmail-prefix scopes for audit storage
     */
    public BundledProvisioningResult provisionBundledOAuth(
            String googleSubject,
            String email,
            String refreshTokenPlaintext,
            String grantedGmailScopes) {

        // FAST PATH: existing user (race-safe re-read).
        var existingUser = userRepository.findByGoogleSubject(googleSubject);
        if (existingUser.isPresent()) {
            UserEntity user = existingUser.get();
            UUID tenantId = user.getTenantId();
            UUID userId = user.getId();
            if (refreshTokenPlaintext == null) {
                // Reconnect with null refresh token — preserve existing envelope.
                log.warn("event=oauth_no_refresh_token tenantId={}", tenantId);
                return new BundledProvisioningResult(tenantId, userId, false);
            }
            // Reconnect with new refresh token — re-encrypt + upsert in single transaction.
            ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                    .run(
                            () ->
                                    bundledTransaction.executeWithoutResult(
                                            _ -> {
                                                byte[] envelope =
                                                        refreshTokenCipher.encrypt(
                                                                refreshTokenPlaintext.getBytes(
                                                                        StandardCharsets.UTF_8),
                                                                tenantId.toString());
                                                gmailConnectionService.upsert(
                                                        tenantId,
                                                        email,
                                                        grantedGmailScopes,
                                                        envelope);
                                                gmailConnectionService.clearForReconnect(tenantId);
                                                // Reconnect MUST NOT regress onboarding_step (D-B4
                                                // invariant).
                                            }));
            return new BundledProvisioningResult(tenantId, userId, false);
        }

        // FIRST-LOGIN PATH: requires non-null refresh token (caller validates; defensive check).
        // This path is only reached when caller contract is violated (first-login null should
        // be caught and thrown as OAuth2AuthenticationException by GoogleOAuthSuccessHandler).
        if (refreshTokenPlaintext == null) {
            throw new IllegalStateException(
                    "First-login provisioning requires a non-null refresh token; "
                            + "caller must have redirected to /login?error=consent_denied before reaching here");
        }

        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        try {
            ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                    .run(
                            () ->
                                    bundledTransaction.executeWithoutResult(
                                            _ -> {
                                                tenantService.createTenant(tenantId, email);
                                                tenantService.setTimeZoneIfAbsent(
                                                        tenantId, TenantEntity.DEFAULT_TIME_ZONE);
                                                UserEntity savedUser =
                                                        userRepository.save(
                                                                new UserEntity(
                                                                        userId,
                                                                        tenantId,
                                                                        googleSubject,
                                                                        email));
                                                byte[] envelope =
                                                        refreshTokenCipher.encrypt(
                                                                refreshTokenPlaintext.getBytes(
                                                                        StandardCharsets.UTF_8),
                                                                tenantId.toString());
                                                notificationPreferenceService.insertDefaults(
                                                        tenantId, ChannelType.EMAIL, true, 20);
                                                gmailConnectionService.upsert(
                                                        tenantId,
                                                        email,
                                                        grantedGmailScopes,
                                                        envelope);
                                                savedUser.advanceTo(OnboardingStep.GMAIL_CONNECTED);
                                                userRepository.save(savedUser);
                                            }));
            log.info("event=oauth_provisioning_complete tenantId={}", tenantId);
            return new BundledProvisioningResult(tenantId, userId, true);
        } catch (DataIntegrityViolationException dataIntegrityViolation) {
            // Concurrent first-login from same Google subject: first transaction rolled back (no
            // orphans).
            // Race-loser: do NOT open a second transaction or overwrite the winner's gmail
            // connection envelope. The winner's thread already committed its own atomic
            // user + tenant + connection in its own bundled transaction — that is the correct,
            // complete state. Opening a second transaction here would break the
            // single-transaction atomicity contract if that second
            // transaction fails, leaving a partial state.
            UserEntity raceWinner =
                    userRepository
                            .findByGoogleSubject(googleSubject)
                            .orElseThrow(() -> dataIntegrityViolation);
            log.warn(
                    "event=oauth_provisioning_race tenantId={} googleSubjectHash={} failureType={}",
                    raceWinner.getTenantId(),
                    Integer.toHexString(googleSubject.hashCode()),
                    dataIntegrityViolation.getClass().getSimpleName());
            return new BundledProvisioningResult(
                    raceWinner.getTenantId(), raceWinner.getId(), false);
        }
    }
}
