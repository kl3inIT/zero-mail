package com.zeromail.core.account;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zeromail.core.persistence.GmailConnectionRepository;
import com.zeromail.core.persistence.OnboardingSelectionRepository;
import com.zeromail.core.persistence.TenantRepository;
import com.zeromail.core.persistence.UserRepository;

/**
 * Owns tenant-scoped account state transitions that controllers used to perform inline.
 * Centralizing this here gives us one place to enforce tenant invariants, transaction
 * boundaries, and (later) audit logging — and lets controllers stay transport-only.
 */
@Service
public class AccountService {

    private final OnboardingSelectionRepository onboarding;
    private final GmailConnectionRepository connections;
    private final UserRepository users;
    private final TenantRepository tenants;

    public AccountService(OnboardingSelectionRepository onboarding,
                          GmailConnectionRepository connections,
                          UserRepository users,
                          TenantRepository tenants) {
        this.onboarding = onboarding;
        this.connections = connections;
        this.users = users;
        this.tenants = tenants;
    }

    /**
     * Returns the canonical current-user view for the tenant, or throws
     * {@link CurrentUserNotFoundException} if the session is bound to a tenant whose user row
     * no longer exists. Returns a {@link CurrentUserView} record so controllers do not need
     * to import the persistence-managed entity type.
     */
    @Transactional(readOnly = true)
    public CurrentUserView requireCurrentUser(UUID tenantId) {
        var user = users.findFirstByTenantId(tenantId)
                .orElseThrow(() -> new CurrentUserNotFoundException(tenantId));
        return new CurrentUserView(
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
     * <p>Goes through JPA dirty-tracking on the managed {@link com.zeromail.core.persistence.UserEntity}
     * — no raw JDBC, no native UPDATE — so Hibernate's {@code @TenantId} filter and the
     * shared-schema multi-tenant invariants stay honored end-to-end. Cross-tenant writes
     * are impossible: the lookup itself is tenant-scoped via {@code findFirstByTenantId}.
     *
     * @param tenantId tenant binding from the active {@code TenantContext} ScopedValue
     * @param language two-letter locale code; caller guarantees membership in {@code {"vi","en"}}
     * @return updated {@link CurrentUserView} reflecting the new {@code preferredLanguage}
     * @throws CurrentUserNotFoundException if no user row exists for the tenant
     */
    @Transactional
    public CurrentUserView updateCurrentUserLanguage(UUID tenantId, String language) {
        var user = users.findFirstByTenantId(tenantId)
                .orElseThrow(() -> new CurrentUserNotFoundException(tenantId));
        user.setPreferredLanguage(language);
        // No explicit save() — the entity is managed and the @Transactional commit will
        // flush the dirty field.
        return new CurrentUserView(
                user.getId(),
                tenantId,
                user.getEmail(),
                user.getOnboardingStep().name(),
                user.getPreferredLanguage());
    }

    /**
     * Cascading delete of every row owned by the tenant, then the tenant itself.
     * Order matters: dependents first, parent last.
     */
    @Transactional
    public void deleteCurrentTenantAccount(UUID tenantId) {
        onboarding.deleteAll(onboarding.findByTenantId(tenantId));
        connections.findByTenantId(tenantId).ifPresent(connections::delete);
        users.findFirstByTenantId(tenantId).ifPresent(users::delete);
        tenants.findById(tenantId).ifPresent(tenants::delete);
    }
}
