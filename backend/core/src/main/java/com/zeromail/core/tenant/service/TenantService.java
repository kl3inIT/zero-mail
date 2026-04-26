package com.zeromail.core.tenant.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zeromail.core.tenant.persistence.TenantRepository;

/**
 * Tenant domain service. First occupant of {@code core.tenant.service.*} (Plan 01.2-05).
 * Currently exposes only {@link #deleteCurrentTenant(UUID)} — the single-domain delete
 * called by {@code AccountDeletionController} per CL-2.
 */
@Service
public class TenantService {

    private final TenantRepository tenants;

    public TenantService(TenantRepository tenants) {
        this.tenants = tenants;
    }

    /**
     * Deletes the tenant row. Caller is responsible for first deleting all child rows
     * (gmail connections, onboarding selections, users) — see {@code AccountDeletionController}.
     */
    @Transactional
    public void deleteCurrentTenant(UUID tenantId) {
        tenants.findById(tenantId).ifPresent(tenants::delete);
    }
}
