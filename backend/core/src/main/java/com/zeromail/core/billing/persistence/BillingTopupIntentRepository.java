package com.zeromail.core.billing.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.zeromail.core.billing.model.BillingTopupIntentStatus;

public interface BillingTopupIntentRepository
        extends JpaRepository<BillingTopupIntentEntity, UUID>, BillingTopupIntentTenantLookupFragment {

    Optional<BillingTopupIntentEntity> findByCode(String code);

    int countByTenantIdAndStatus(UUID tenantId, BillingTopupIntentStatus status);

    Optional<BillingTopupIntentEntity> findFirstByTenantIdAndStatusOrderByCreatedAtAsc(
            UUID tenantId, BillingTopupIntentStatus status);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE billing_topup_intent
               SET status = 'EXPIRED',
                   updated_at = CURRENT_TIMESTAMP
             WHERE status = 'PENDING'
               AND expires_at < :now
            """, nativeQuery = true)
    int expireStale(@Param("now") Instant now);
}
