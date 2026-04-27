package com.zeromail.core.account.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.gmail.service.GmailConnectionService;
import com.zeromail.core.onboarding.model.OnboardingStep;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.service.TenantService;

/**
 * Atomic provisioning for the OAuth first-login flow.
 *
 * <p>Phase 01.5 HIGH-1 fix: the new {@link #provisionBundledOAuth} method collapses
 * user + tenant + GmailConnectionEntity creation into ONE TransactionTemplate
 * (PROPAGATION_REQUIRED) so any failure rolls back all three — no half-provisioned state.
 * The original {@link #findOrCreateGoogleUser} method is preserved (it remains the basis
 * for the bundled path's race-handling logic) but is no longer called by
 * {@code GoogleOAuthSuccessHandler} directly.
 *
 * <p>ScopedValue binding invariant (FND-05): {@link TenantContext#TENANT} MUST be bound
 * BEFORE the TransactionTemplate opens (Hibernate captures tenant on JPA session open).
 *
 * <p><b>API surface:</b> accepts plain {@code String} subject/email rather than
 * {@code OidcUser} so this module does not need to depend on Spring Security's OAuth2
 * module (preserves {@code allowedDependencies} boundary in {@code core.account}).
 */
@Service
public class OAuthProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(OAuthProvisioningService.class);

    private final UserRepository users;
    private final TenantService tenantService;
    private final GmailConnectionService connections;
    private final RefreshTokenCipher cipher;
    private final TransactionTemplate provisioningTx;
    private final TransactionTemplate bundledTx;

    public OAuthProvisioningService(UserRepository users,
                                    TenantService tenantService,
                                    GmailConnectionService connections,
                                    RefreshTokenCipher cipher,
                                    PlatformTransactionManager txManager) {
        this.users = users;
        this.tenantService = tenantService;
        this.connections = connections;
        this.cipher = cipher;
        this.provisioningTx = new TransactionTemplate(txManager);
        this.provisioningTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.bundledTx = new TransactionTemplate(txManager);
        this.bundledTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    /**
     * Result record for {@link #provisionBundledOAuth}.
     */
    public record BundledProvisioningResult(UUID tenantId, UUID userId, boolean firstLogin) {}

    /**
     * Atomic bundled provisioning (HIGH-1 fix): creates user + tenant + GmailConnectionEntity
     * + advances onboarding step inside ONE {@code PROPAGATION_REQUIRED} transaction.
     * Any failure in any step causes a full rollback — no half-provisioned rows survive.
     *
     * <p>Null {@code refreshTokenPlaintext} handling (MED-3):
     * <ul>
     *   <li>Existing user (reconnect path): preserves existing GmailConnectionEntity envelope
     *       unchanged; emits opaque warning log {@code event=oauth_no_refresh_token}.</li>
     *   <li>No existing user (first-login path): throws
     *       {@link OAuth2AuthenticationException}{@code ("consent_denied")} — caller must
     *       have validated this before calling (defensive check here).</li>
     * </ul>
     *
     * <p><b>Privacy (T-01.5-01-02):</b> raw refresh-token bytes are encrypted inside the
     * same transaction boundary and NEVER logged. Event names are opaque.
     *
     * @param googleSubject   Google OIDC {@code sub} claim
     * @param email           Google OIDC {@code email} claim
     * @param refreshTokenPlaintext  raw refresh-token string; may be {@code null} on reconnect
     * @param grantedGmailScopes    space-joined sorted gmail-prefix scopes for audit storage
     */
    public BundledProvisioningResult provisionBundledOAuth(
            String googleSubject,
            String email,
            String refreshTokenPlaintext,
            String grantedGmailScopes) {

        // FAST PATH: existing user (race-safe re-read).
        var existing = users.findByGoogleSubject(googleSubject);
        if (existing.isPresent()) {
            UserEntity u = existing.get();
            UUID tenantId = u.getTenantId();
            UUID userId = u.getId();
            if (refreshTokenPlaintext == null) {
                // MED-3: reconnect with null refresh token — preserve existing envelope.
                log.warn("event=oauth_no_refresh_token tenantId={}", tenantId);
                return new BundledProvisioningResult(tenantId, userId, false);
            }
            // Reconnect with new refresh token — re-encrypt + upsert in single tx.
            ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                    .run(() -> bundledTx.executeWithoutResult(_ -> {
                        byte[] envelope = cipher.encrypt(
                                refreshTokenPlaintext.getBytes(StandardCharsets.UTF_8),
                                tenantId.toString());
                        connections.upsert(tenantId, email, grantedGmailScopes, envelope);
                        // Reconnect MUST NOT regress onboarding_step (D-B4 invariant).
                    }));
            return new BundledProvisioningResult(tenantId, userId, false);
        }

        // FIRST-LOGIN PATH: requires non-null refresh token (caller validates; defensive check).
        // This path is only reached when caller contract is violated (first-login null should
        // be caught and thrown as OAuth2AuthenticationException by GoogleOAuthSuccessHandler).
        if (refreshTokenPlaintext == null) {
            throw new IllegalStateException(
                    "First-login provisioning requires a non-null refresh token; " +
                    "caller must have redirected to /login?error=consent_denied before reaching here");
        }

        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        try {
            ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                    .run(() -> bundledTx.executeWithoutResult(_ -> {
                        tenantService.createTenant(tenantId, email);
                        UserEntity saved = users.save(new UserEntity(
                                userId, tenantId, googleSubject, email));
                        byte[] envelope = cipher.encrypt(
                                refreshTokenPlaintext.getBytes(StandardCharsets.UTF_8),
                                tenantId.toString());
                        connections.upsert(tenantId, email, grantedGmailScopes, envelope);
                        saved.advanceTo(OnboardingStep.GMAIL_CONNECTED);
                        users.save(saved);
                    }));
            log.info("event=oauth_provisioning_complete tenantId={}", tenantId);
            return new BundledProvisioningResult(tenantId, userId, true);
        } catch (DataIntegrityViolationException race) {
            // Concurrent first-login from same Google subject: tx rolled back (no orphans).
            log.warn("event=oauth_provisioning_race");
            UserEntity racedWinner = users.findByGoogleSubject(googleSubject)
                    .orElseThrow(() -> race);
            // Race-loser: upsert gmail connection under winner's tenant in a fresh tx.
            UUID winnerTenant = racedWinner.getTenantId();
            ScopedValue.where(TenantContext.TENANT, winnerTenant.toString())
                    .run(() -> bundledTx.executeWithoutResult(_ -> {
                        byte[] envelope = cipher.encrypt(
                                refreshTokenPlaintext.getBytes(StandardCharsets.UTF_8),
                                winnerTenant.toString());
                        connections.upsert(winnerTenant, email, grantedGmailScopes, envelope);
                    }));
            return new BundledProvisioningResult(winnerTenant, racedWinner.getId(), false);
        }
    }

    /**
     * Returns the existing user for the Google subject, or creates a tenant + user pair.
     * Preserved for safety (no callers remain after Phase 01.5 pivot, but kept as an
     * internal seam). On a duplicate-Google-subject race, rolls back the inner REQUIRES_NEW
     * tx and re-reads so both callers converge on the same {@link UserEntity}.
     *
     * @deprecated Use {@link #provisionBundledOAuth} instead (Phase 01.5 HIGH-1 atomicity fix).
     */
    @Deprecated(since = "01.5", forRemoval = true)
    public UserEntity findOrCreateGoogleUser(String googleSubject, String email) {
        return users.findByGoogleSubject(googleSubject)
                .orElseGet(() -> tryCreateOrReadAfterRace(googleSubject, email));
    }

    private UserEntity tryCreateOrReadAfterRace(String googleSubject, String email) {
        try {
            return createTenantAndUser(googleSubject, email);
        } catch (DataIntegrityViolationException race) {
            log.warn("event=oauth_provisioning_race_legacy");
            return users.findByGoogleSubject(googleSubject)
                    .orElseThrow(() -> race);
        }
    }

    private UserEntity createTenantAndUser(String googleSubject, String email) {
        UUID tenantId = UUID.randomUUID();
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(() -> provisioningTx.execute(_ -> {
                    tenantService.createTenant(tenantId, email);
                    return users.save(new UserEntity(
                            UUID.randomUUID(), tenantId, googleSubject, email));
                }));
    }
}
