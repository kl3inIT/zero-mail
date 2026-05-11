package com.zeromail.core.onboarding.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OnboardingSelectionRepository
        extends JpaRepository<OnboardingSelectionEntity, UUID> {

    List<OnboardingSelectionEntity> findByTenantId(UUID tenantId);

    /**
     * Bulk JPQL delete of all selections for a tenant — single statement (closes REVIEW WR-03).
     *
     * <p><b>Tenant isolation contract:</b> the explicit {@code WHERE o.tenantId = :tenantId} is the
     * security boundary. Hibernate's {@code @TenantId} discriminator filter is NOT applied to bulk
     * JPQL — see {@code core.shared.persistence/package-info.java} D-A5 caveat. Caller MUST pass
     * the resolved current-tenant id from {@code TenantContext}.
     *
     * <p><b>Audit caveat (D-A5):</b> bulk JPQL bypasses Spring Data JPA's {@code
     * AuditingEntityListener} — irrelevant for DELETE (no row remains to audit) but documented for
     * symmetry with future bulk UPDATE additions.
     *
     * @return number of rows deleted (Spring Data JPA exposes the row-count int).
     */
    @Modifying
    @Query("DELETE FROM OnboardingSelectionEntity o WHERE o.tenantId = :tenantId")
    int deleteByTenantId(@Param("tenantId") UUID tenantId);
}
