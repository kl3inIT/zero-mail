package com.zeromail.core.account.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zeromail.core.account.exception.CurrentUserNotFoundException;
import com.zeromail.core.account.projection.CurrentUserProjection;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.onboarding.domain.OnboardingStep;

/**
 * Owns tenant-scoped account state transitions that controllers used to perform inline.
 * Centralizing this here gives us one place to enforce tenant invariants, transaction
 * boundaries, and (later) audit logging — and lets controllers stay transport-only.
 *
 * <p>Phase 1.2 reshape (CL-2 + D-D1): the previous incarnation injected four
 * cross-domain repositories (gmail, onboarding, user, tenant) to perform a cascading
 * delete inline. After Phase 1.2 only {@link UserRepository} remains — the multi-domain
 * delete orchestration moves to {@code AccountDeletionController} (transitional bridge
 * during Plans 03–05; finalized in Plan 06 as a chain of per-domain service calls).
 */
@Service
public class AccountService {

    private final UserRepository users;

    public AccountService(UserRepository users) {
        this.users = users;
    }

    /**
     * Returns the canonical current-user projection for the tenant, or throws
     * {@link CurrentUserNotFoundException} if the session is bound to a tenant whose user row
     * no longer exists. Returns a {@link CurrentUserProjection} record so controllers do not need
     * to import the persistence-managed entity type.
     */
    @Transactional(readOnly = true)
    public CurrentUserProjection requireCurrentUser(UUID tenantId) {
        var user = users.findFirstByTenantId(tenantId)
                .orElseThrow(() -> new CurrentUserNotFoundException(tenantId));
        return new CurrentUserProjection(
                user.getId(),
                tenantId,
                user.getEmail(),
                user.getOnboardingStep().name(),
                user.getPreferredLanguage());
    }

    /**
     * Persists the caller's preferred language ({@code 'vi'} or {@code 'en'}). The argument
     * is assumed to have already passed Bean Validation ({@code @Pattern(regexp = "vi|en")}
     * on {@code UpdateLanguageRequest}); this method does NOT re-validate the allow-list,
     * because controller-side validation is the single source of truth for that contract.
     *
     * <p>Goes through JPA dirty-tracking on the managed
     * {@link com.zeromail.core.account.persistence.UserEntity}
     * — no raw JDBC, no native UPDATE — so Hibernate's {@code @TenantId} filter and the
     * shared-schema multi-tenant invariants stay honored end-to-end. Cross-tenant writes
     * are impossible: the lookup itself is tenant-scoped via {@code findFirstByTenantId}.
     *
     * @param tenantId tenant binding from the active {@code TenantContext} ScopedValue
     * @param language two-letter locale code; caller guarantees membership in {@code {"vi","en"}}
     * @return updated {@link CurrentUserProjection} reflecting the new {@code preferredLanguage}
     * @throws CurrentUserNotFoundException if no user row exists for the tenant
     */
    @Transactional
    public CurrentUserProjection updateCurrentUserLanguage(UUID tenantId, String language) {
        var user = users.findFirstByTenantId(tenantId)
                .orElseThrow(() -> new CurrentUserNotFoundException(tenantId));
        user.setPreferredLanguage(language);
        // No explicit save() — the entity is managed and the @Transactional commit will
        // flush the dirty field.
        return new CurrentUserProjection(
                user.getId(),
                tenantId,
                user.getEmail(),
                user.getOnboardingStep().name(),
                user.getPreferredLanguage());
    }

    /**
     * Deletes the User row for the current tenant. Single-domain delete only —
     * the multi-domain orchestration (gmail connection, onboarding selections, tenant)
     * lives in AccountDeletionController per CL-2 + D-D1.
     */
    @Transactional
    public void deleteCurrentUser(UUID tenantId) {
        users.findFirstByTenantId(tenantId).ifPresent(users::delete);
    }

    /**
     * Advances the onboarding state machine for the current tenant's user.
     * Exposed so {@code OnboardingService} (the {@code onboarding} domain) does NOT
     * have to inject the cross-domain {@link UserRepository} (D-D1 — enforced by
     * {@code DomainBoundaryArchTests}). The state-machine forward-only invariant is
     * enforced inside {@code UserEntity#advanceTo}.
     *
     * @throws CurrentUserNotFoundException if no user row exists for the tenant
     */
    @Transactional
    public void advanceOnboardingStep(UUID tenantId, OnboardingStep next) {
        var user = users.findFirstByTenantId(tenantId)
                .orElseThrow(() -> new CurrentUserNotFoundException(tenantId));
        user.advanceTo(next);
        // No explicit save() — managed entity flushes on commit.
    }
}
