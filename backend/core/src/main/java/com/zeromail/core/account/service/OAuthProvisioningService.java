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
 * <p>Phase 01.5 HIGH-1 fix: {@link #provisionBundledOAuth} collapses
 * user + tenant + GmailConnectionEntity creation into ONE TransactionTemplate
 * (PROPAGATION_REQUIRED) so any failure rolls back all three — no half-provisioned state.
 *
 * <p>Phase 01.5 WR-01 cleanup: the legacy two-leg {@code findOrCreateGoogleUser} entry point
 * (and its {@code provisioningTx} PROPAGATION_REQUIRES_NEW template) was deleted along with
 * the bundled-flow collapse. {@link #provisionBundledOAuth} is now the single provisioning
 * surface.
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
     *   <li>No existing user (first-login path): throws {@code IllegalStateException("consent_denied")}
     *       — caller ({@code GoogleOAuthSuccessHandler}) must have validated this before calling
     *       (this is a defensive check; the handler throws {@code OAuth2AuthenticationException}
     *       before reaching here).</li>
     * </ul>
     *
     * <p>Race-loser path ({@code DataIntegrityViolationException} on {@code users.save}):
     * observes the winner's already-complete state, returns
     * {@code BundledProvisioningResult(winner tenant, winner id, false)} without any further
     * DB write — preserving the single-transaction guarantee (CR-01 fix).
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
                        connections.clearForReconnect(tenantId);
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
            // Concurrent first-login from same Google subject: first tx rolled back (no orphans).
            // Race-loser: do NOT open a second transaction or overwrite the winner's gmail
            // connection envelope. The winner's thread already committed its own atomic
            // user + tenant + connection in its own bundledTx — that is the correct, complete
            // state. Opening a second tx here would break the single-transaction atomicity
            // contract (CR-01 / HIGH-1 fix) if that second tx fails, leaving a partial state.
            log.warn("event=oauth_provisioning_race");
            UserEntity racedWinner = users.findByGoogleSubject(googleSubject)
                    .orElseThrow(() -> race);
            return new BundledProvisioningResult(racedWinner.getTenantId(), racedWinner.getId(), false);
        }
    }
}
