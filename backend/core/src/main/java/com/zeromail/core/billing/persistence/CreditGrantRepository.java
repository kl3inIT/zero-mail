package com.zeromail.core.billing.persistence;

import com.zeromail.core.billing.domain.CreditGrantCategory;
import com.zeromail.core.billing.domain.CreditGrantStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditGrantRepository extends JpaRepository<CreditGrantEntity, UUID> {

    Optional<CreditGrantEntity> findByTenantIdAndCategoryAndRefTypeAndRefId(
            UUID tenantId, CreditGrantCategory category, String refType, String refId);

    List<CreditGrantEntity> findByTenantIdAndStatusAndExpiresAtBefore(
            UUID tenantId, CreditGrantStatus status, Instant now);

    @Query(
            """
            SELECT creditGrant
              FROM CreditGrantEntity creditGrant
             WHERE creditGrant.tenantId = :tenantId
               AND creditGrant.status = com.zeromail.core.billing.domain.CreditGrantStatus.ACTIVE
               AND creditGrant.effectiveAt <= :now
               AND (creditGrant.expiresAt IS NULL OR creditGrant.expiresAt > :now)
               AND (
                    SELECT COALESCE(SUM(ledgerEntry.amountCredits), 0)
                      FROM CreditLedgerEntryEntity ledgerEntry
                     WHERE ledgerEntry.tenantId = :tenantId
                       AND ledgerEntry.grantId = creditGrant.id
               ) >= :requiredCredits
             ORDER BY creditGrant.priority ASC,
                      CASE WHEN creditGrant.expiresAt IS NULL THEN 1 ELSE 0 END ASC,
                      creditGrant.expiresAt ASC,
                      creditGrant.createdAt ASC
            """)
    List<CreditGrantEntity> findSpendableGrants(
            @Param("tenantId") UUID tenantId,
            @Param("now") Instant now,
            @Param("requiredCredits") int requiredCredits);
}
