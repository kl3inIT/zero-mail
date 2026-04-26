package com.zeromail.core.account.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
 * <p>Implementation note: the create path runs in a {@code REQUIRES_NEW} transaction so that a
 * unique-constraint race (caught as {@link DataIntegrityViolationException}) rolls back the
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
    private final OAuthProvisioningService self;

    public OAuthProvisioningService(UserRepository users,
                                    TenantService tenantService,
                                    @Lazy OAuthProvisioningService self) {
        this.users = users;
        this.tenantService = tenantService;
        this.self = self;
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
            return self.createTenantAndUser(googleSubject, email);
        } catch (DataIntegrityViolationException race) {
            // Concurrent first-login from the same Google subject lost the race. The inner
            // REQUIRES_NEW transaction rolled back, so no orphan tenant remains.
            // Deliberately NOT logging the email or subject (privacy contract).
            log.warn("OAuth provisioning lost a unique-constraint race; converging on existing user");
            return users.findByGoogleSubject(googleSubject)
                    .orElseThrow(() -> race);
        }
    }

    /**
     * Creates the tenant and user in a single new transaction. Public so the Spring proxy can
     * apply {@link Transactional}; the lazy self-reference re-enters through the proxy.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserEntity createTenantAndUser(String googleSubject, String email) {
        UUID tenantId = UUID.randomUUID();
        tenantService.createTenant(tenantId, email);
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(() -> users.save(new UserEntity(
                        UUID.randomUUID(), tenantId, googleSubject, email)));
    }
}
