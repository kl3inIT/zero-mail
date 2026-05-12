package com.zeromail.core.billing.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditLedgerEntryRepository extends JpaRepository<CreditLedgerEntryEntity, UUID> {

    @Query(
            "SELECT COALESCE(SUM(entry.amountCredits), 0) FROM CreditLedgerEntryEntity entry WHERE entry.tenantId = :tenantId")
    long sumAvailableCreditsForTenant(@Param("tenantId") UUID tenantId);

    /**
     * Held credits are RESERVE debits that have not yet been finalized by SETTLE or RELEASE. The
     * signed journal stores RESERVE as negative, so this returns the positive held amount.
     */
    @Query(
            """
            SELECT COALESCE(-SUM(entry.amountCredits), 0)
              FROM CreditLedgerEntryEntity entry
             WHERE entry.tenantId = :tenantId
               AND entry.kind = 'RESERVE'
               AND NOT EXISTS (
                    SELECT 1
                      FROM CreditLedgerEntryEntity finalizingEntry
                     WHERE finalizingEntry.refType = 'RESERVATION'
                       AND finalizingEntry.refId = entry.refId
                       AND finalizingEntry.kind IN ('SETTLE', 'RELEASE')
               )
            """)
    long sumHeldCreditsForTenant(@Param("tenantId") UUID tenantId);
}
