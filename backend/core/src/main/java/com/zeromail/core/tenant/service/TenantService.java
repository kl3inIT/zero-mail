package com.zeromail.core.tenant.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;

/**
 * Tenant domain service. First occupant of {@code core.tenant.service.*} (Plan 01.2-05).
 *
 * <p>Exposes single-domain primitives so peer domains never need to inject
 * {@link TenantRepository} directly (D-D1 — enforced by {@code DomainBoundaryArchTests}).
 * Multi-domain orchestration is the API tier's responsibility (e.g.,
 * {@code AccountDeletionController} for the delete cascade,
 * {@code OAuthProvisioningService} for first-login provisioning which still owns
 * its own {@code REQUIRES_NEW} transaction).
 */
@Service
public class TenantService {

    private final TenantRepository tenants;

    public TenantService(TenantRepository tenants) {
        this.tenants = tenants;
    }

    /**
     * Persists a new tenant row. Used by {@code OAuthProvisioningService} during the
     * first-login flow so that the cross-domain {@link TenantRepository} no longer needs
     * to be injected into the {@code account} domain (D-D1).
     */
    @Transactional
    public TenantEntity createTenant(UUID tenantId, String displayName) {
        return tenants.save(new TenantEntity(tenantId, displayName));
    }

    /**
     * Deletes the tenant row. Caller is responsible for first deleting all child rows
     * (gmail connections, onboarding selections, users) — see {@code AccountDeletionController}.
     */
    @Transactional
    public void deleteCurrentTenant(UUID tenantId) {
        tenants.findById(tenantId).ifPresent(tenants::delete);
    }

    @Transactional
    public void setTriagePaused(UUID tenantId, boolean paused) {
        tenants.findById(tenantId).ifPresent(t -> {
            t.setTriagePaused(paused);
            tenants.save(t);
        });
    }
}
