package com.zeromail.core.tenant.usecases;

import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant domain service. First occupant of {@code core.tenant.usecases.*} (Plan 01.2-05).
 *
 * <p>Exposes single-domain primitives so peer domains never need to inject {@link TenantRepository}
 * directly (D-D1 — enforced by {@code DomainBoundaryArchTests}). Multi-domain orchestration is the
 * API tier's responsibility (e.g., {@code AccountDeletionController} for the delete cascade, {@code
 * OAuthProvisioningService} for first-login provisioning which still owns its own {@code
 * REQUIRES_NEW} transaction).
 */
@Service
public class TenantService {

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    /**
     * Persists a new tenant row. Used by {@code OAuthProvisioningService} during the first-login
     * flow so that the cross-domain {@link TenantRepository} no longer needs to be injected into
     * the {@code account} domain (D-D1).
     */
    @Transactional
    public TenantEntity createTenant(UUID tenantId, String displayName) {
        return tenantRepository.save(new TenantEntity(tenantId, displayName));
    }

    @Transactional
    public void setTimeZoneIfAbsent(UUID tenantId, String ianaZone) {
        tenantRepository
                .findById(tenantId)
                .ifPresent(
                        tenant -> {
                            if (TenantEntity.DEFAULT_TIME_ZONE.equals(tenant.getTimeZone())) {
                                tenant.setTimeZone(ianaZone);
                            }
                        });
    }

    /**
     * Deletes the tenant row. Caller is responsible for first deleting all child rows (gmail
     * connections, onboarding selections, users) — see {@code AccountDeletionController}.
     */
    @Transactional
    public void deleteCurrentTenant(UUID tenantId) {
        tenantRepository.findById(tenantId).ifPresent(tenantRepository::delete);
    }

    @Transactional
    public void setTriagePaused(UUID tenantId, boolean paused) {
        tenantRepository.findById(tenantId).ifPresent(tenant -> tenant.setTriagePaused(paused));
    }

    @Transactional(readOnly = true)
    public boolean isTriagePaused(UUID tenantId) {
        return tenantRepository.findById(tenantId).map(TenantEntity::isTriagePaused).orElse(false);
    }

    /**
     * Reads triage flags in a single query so hot-path callers like the triage orchestrator do not
     * issue two separate {@code findById} round trips per inbound message.
     */
    @Transactional(readOnly = true)
    public TenantTriageSettings triageSettingsFor(UUID tenantId) {
        return tenantRepository
                .findById(tenantId)
                .map(tenant -> new TenantTriageSettings(tenant.isTriagePaused()))
                .orElse(TenantTriageSettings.defaults());
    }

    public record TenantTriageSettings(boolean paused) {
        public static TenantTriageSettings defaults() {
            return new TenantTriageSettings(false);
        }
    }

    @Transactional(readOnly = true)
    public String timeZoneFor(UUID tenantId) {
        return tenantRepository
                .findById(tenantId)
                .map(TenantEntity::getTimeZone)
                .orElse(TenantEntity.DEFAULT_TIME_ZONE);
    }
}
