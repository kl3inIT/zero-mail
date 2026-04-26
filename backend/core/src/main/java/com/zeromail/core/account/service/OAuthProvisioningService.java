package com.zeromail.core.account.service;

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
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.service.TenantService;

/**
 * Atomic provisioning for the OAuth first-login flow. Wraps tenant + user creation in a single
 * transaction and converges concurrent first-login races on the same user row.
 *
 * <p>Without this, the previous handler could orphan a tenant if the user insert failed,
 * or could create two tenants for the same Google subject under contention.
 *
 * <p>Implementation note: the create path binds {@link TenantContext} before opening a
 * {@code REQUIRES_NEW} transaction. Hibernate captures the current tenant when the JPA session
 * opens; opening the tx first would capture the bootstrap tenant and reject the user insert.
 * A unique-constraint race (caught as {@link DataIntegrityViolationException}) rolls back the
 * inner tx (including the freshly created tenant row, avoiding orphans) while leaving the
 * outer caller free to re-read the existing user.
 *
 * <p>Note: the API surface accepts plain {@code String} subject/email rather than
 * {@code OidcUser} so this module does not need to depend on Spring Security's OAuth2 module
 * (which would push core's allowed-dependencies graph beyond persistence + tenant).
 */
@Service
public class OAuthProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(OAuthProvisioningService.class);

    private final UserRepository users;
    private final TenantService tenantService;
    private final TransactionTemplate provisioningTx;

    public OAuthProvisioningService(UserRepository users,
                                    TenantService tenantService,
                                    PlatformTransactionManager txManager) {
        this.users = users;
        this.tenantService = tenantService;
        this.provisioningTx = new TransactionTemplate(txManager);
        this.provisioningTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Returns the existing user for the Google subject, or creates a tenant + user pair.
     * On a duplicate-Google-subject race (two concurrent first-logins), the second insert
     * fails the user-table unique constraint; we roll back the inner tx and re-read so both
     * callers converge on the same {@link UserEntity}.
     */
    public UserEntity findOrCreateGoogleUser(String googleSubject, String email) {
        return users.findByGoogleSubject(googleSubject)
                .orElseGet(() -> tryCreateOrReadAfterRace(googleSubject, email));
    }

    private UserEntity tryCreateOrReadAfterRace(String googleSubject, String email) {
        try {
            return createTenantAndUser(googleSubject, email);
        } catch (DataIntegrityViolationException race) {
            // Concurrent first-login from the same Google subject lost the race. The inner
            // REQUIRES_NEW transaction rolled back, so no orphan tenant remains.
            // Deliberately NOT logging the email or subject (privacy contract).
            log.warn("OAuth provisioning lost a unique-constraint race; converging on existing user");
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
