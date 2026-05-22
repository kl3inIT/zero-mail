package com.zeromail.core.admin.spend.projection;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate snapshot returned by {@code SpendAggregateQueryService}. Defense-in-depth privacy
 * contract: this record carries only aggregates — no per-prompt text, no per-completion text, no
 * message body, no model arguments. {@code kAnonymityFooterNote} carries the UI-SPEC line 213
 * microcopy verbatim so the controller does not have to rebuild it.
 *
 * <p>{@code unknownPercentOfTotal} is the share of {@code totalCost} (across all rows in the range)
 * attributable to historical {@code credential_source = 'UNKNOWN'} rows per R-8F-H9. When > 0, the
 * UI renders the "Unknown" stacked-bar segment + caveat caption.
 */
public record SpendDashboardSnapshot(
        SpendKpis kpis,
        List<ProviderStackBarRow> stackBar,
        List<FeatureDonutSlice> donut,
        List<TopTenantRow> topTenants,
        Instant snapshotAt,
        String kAnonymityFooterNote,
        double unknownPercentOfTotal) {

    public SpendDashboardSnapshot {
        Objects.requireNonNull(kpis, "kpis must not be null");
        Objects.requireNonNull(stackBar, "stackBar must not be null");
        Objects.requireNonNull(donut, "donut must not be null");
        Objects.requireNonNull(topTenants, "topTenants must not be null");
        Objects.requireNonNull(snapshotAt, "snapshotAt must not be null");
        Objects.requireNonNull(kAnonymityFooterNote, "kAnonymityFooterNote must not be null");
        stackBar = List.copyOf(stackBar);
        donut = List.copyOf(donut);
        topTenants = List.copyOf(topTenants);
    }
}
