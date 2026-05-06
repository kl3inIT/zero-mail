package com.zeromail.core.billing.persistence;

import java.time.Instant;
import java.util.List;

public interface CreditReservationStaleScanFragment {

    /**
     * Selects stale PENDING reservations without relying on Hibernate tenant filtering.
     * Returned rows include tenant id so the worker can bind TenantContext before release.
     */
    List<StaleReservation> findStalePendingProjections(Instant olderThan, int limitRows);
}
