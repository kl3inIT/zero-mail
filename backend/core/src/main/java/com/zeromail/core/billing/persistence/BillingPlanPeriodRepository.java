package com.zeromail.core.billing.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingPlanPeriodRepository extends JpaRepository<BillingPlanPeriodEntity, UUID> {

    Optional<BillingPlanPeriodEntity> findByProviderOrderId(String providerOrderId);

    @Query(
            """
            SELECT planPeriod
              FROM BillingPlanPeriodEntity planPeriod
             WHERE planPeriod.tenantId = :tenantId
               AND planPeriod.status = 'ACTIVE'
               AND planPeriod.effectiveAt <= :now
               AND planPeriod.expiresAt > :now
             ORDER BY planPeriod.effectiveAt DESC, planPeriod.id DESC
            """)
    List<BillingPlanPeriodEntity> findCurrentTenantPlanPeriods(
            @Param("tenantId") UUID tenantId, @Param("now") Instant now);

    @Query(
            """
            SELECT planPeriod
              FROM BillingPlanPeriodEntity planPeriod
             WHERE planPeriod.tenantId = :tenantId
               AND planPeriod.status = 'ACTIVE'
               AND planPeriod.id <> :excludedPlanPeriodId
               AND planPeriod.effectiveAt < :expiresAt
               AND planPeriod.expiresAt > :effectiveAt
            """)
    List<BillingPlanPeriodEntity> findOverlappingActiveTenantPlanPeriods(
            @Param("tenantId") UUID tenantId,
            @Param("excludedPlanPeriodId") UUID excludedPlanPeriodId,
            @Param("effectiveAt") Instant effectiveAt,
            @Param("expiresAt") Instant expiresAt);

    @Query(
            """
            SELECT planPeriod
              FROM BillingPlanPeriodEntity planPeriod
             WHERE planPeriod.tenantId = :tenantId
               AND planPeriod.status <> 'VOIDED'
               AND planPeriod.expiresAt > :after
               AND planPeriod.expiresAt <= :now
             ORDER BY planPeriod.expiresAt DESC, planPeriod.id DESC
            """)
    List<BillingPlanPeriodEntity> findLatestEndedTenantPlanPeriodAfter(
            @Param("tenantId") UUID tenantId,
            @Param("after") Instant after,
            @Param("now") Instant now);
}
