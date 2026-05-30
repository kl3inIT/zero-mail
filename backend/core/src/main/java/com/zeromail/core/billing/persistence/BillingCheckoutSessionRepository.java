package com.zeromail.core.billing.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingCheckoutSessionRepository
        extends JpaRepository<BillingCheckoutSessionEntity, UUID> {

    List<BillingCheckoutSessionEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    @Query(
            value =
                    """
                    SELECT *
                    FROM billing_checkout_session
                    WHERE tenant_id = :tenantId
                      AND plan_code = :planCode
                      AND (
                        (:userEmail IS NULL AND user_email IS NULL)
                        OR user_email = :userEmail
                      )
                      AND status = :status
                      AND reuse_expires_at > :now
                    ORDER BY created_at DESC
                    LIMIT 1
                    """,
            nativeQuery = true)
    Optional<BillingCheckoutSessionEntity> findReusableCheckoutSession(
            @Param("tenantId") UUID tenantId,
            @Param("planCode") String planCode,
            @Param("userEmail") String userEmail,
            @Param("status") String status,
            @Param("now") Instant now);
}
