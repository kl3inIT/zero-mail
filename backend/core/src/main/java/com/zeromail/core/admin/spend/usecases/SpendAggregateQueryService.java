package com.zeromail.core.admin.spend.usecases;

import com.zeromail.core.admin.config.AdminProperties;
import com.zeromail.core.admin.spend.persistence.lowlevel.SpendAggregateReadRepository;
import com.zeromail.core.admin.spend.projection.FeatureDonutSlice;
import com.zeromail.core.admin.spend.projection.FeatureSpendBucket;
import com.zeromail.core.admin.spend.projection.ProviderStackBarRow;
import com.zeromail.core.admin.spend.projection.SpendDashboardSnapshot;
import com.zeromail.core.admin.spend.projection.SpendKpis;
import com.zeromail.core.admin.spend.projection.SpendQuery;
import com.zeromail.core.admin.spend.projection.TenantSpendBucket;
import com.zeromail.core.admin.spend.projection.TopTenantRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the admin spend dashboard. Raw SQL lives in {@link SpendAggregateReadRepository};
 * this service applies money rescaling, donut percentage derivation, and the k-anonymity collapse
 * over per-tenant buckets.
 *
 * <p>K-anonymity (R-8F-H6, default k = 5): top-tenants buckets with fewer than k distinct tenants
 * (or any row with a null tenant) collapse into a single "K-anonymized aggregate" / "[deleted]"
 * rollup row.
 *
 * <p>Platform-vs-BYOK classification (R-8F-H1): each row in {@code llm_call_audit} carries a {@code
 * credential_source} column with values {@code PLATFORM | BYOK | UNKNOWN}; the repository
 * aggregates the three buckets per slice; UNKNOWN is reserved for historical rows predating the
 * row-level classification rollout (R-8F-H9).
 */
@Service
public class SpendAggregateQueryService {

    private static final int TOP_TENANTS_LIMIT = 20;
    private static final BigDecimal MONEY_SCALE_ZERO = BigDecimal.ZERO.setScale(6);
    private static final String K_ANONYMITY_FOOTER_NOTE =
            "Per-tenant spend bucketed by 7-day k-anonymity (k≥5)."
                    + " Exact per-tenant cost is not exposed.";
    private static final String DELETED_ROLLUP_PLACEHOLDER = "[deleted]";
    private static final String K_ANONYMIZED_AGGREGATE_PLACEHOLDER = "K-anonymized aggregate";

    private final SpendAggregateReadRepository spendAggregateReadRepository;
    private final Clock clock;
    private final int kAnonymityThreshold;

    public SpendAggregateQueryService(
            SpendAggregateReadRepository spendAggregateReadRepository,
            Clock clock,
            AdminProperties adminProperties) {
        this.spendAggregateReadRepository =
                Objects.requireNonNull(
                        spendAggregateReadRepository,
                        "spendAggregateReadRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(adminProperties, "adminProperties must not be null");
        this.kAnonymityThreshold = adminProperties.spend().kAnonymityThreshold();
    }

    @Transactional(readOnly = true)
    public SpendDashboardSnapshot snapshot(SpendQuery spendQuery) {
        Objects.requireNonNull(spendQuery, "spendQuery must not be null");
        Instant now = clock.instant();
        SpendKpis kpis = rescaleKpis(loadKpis(now));
        List<ProviderStackBarRow> stackBar = rescaleStackBar(loadStackBar(spendQuery));
        List<FeatureDonutSlice> donut = computeDonut(loadFeatureBuckets(spendQuery));
        List<TopTenantRow> topTenants = applyKAnonymity(loadTenantBuckets(spendQuery));
        double unknownPercent = computeUnknownPercent(stackBar);
        return new SpendDashboardSnapshot(
                kpis, stackBar, donut, topTenants, now, K_ANONYMITY_FOOTER_NOTE, unknownPercent);
    }

    private SpendKpis loadKpis(Instant now) {
        return spendAggregateReadRepository.findKpis(
                now.minus(Duration.ofDays(1)),
                now.minus(Duration.ofDays(7)),
                now.minus(Duration.ofDays(30)));
    }

    private List<ProviderStackBarRow> loadStackBar(SpendQuery spendQuery) {
        return spendAggregateReadRepository.findProviderStackBarRows(spendQuery);
    }

    private List<FeatureSpendBucket> loadFeatureBuckets(SpendQuery spendQuery) {
        return spendAggregateReadRepository.findFeatureBuckets(spendQuery);
    }

    private List<TenantSpendBucket> loadTenantBuckets(SpendQuery spendQuery) {
        return spendAggregateReadRepository.findTopTenantBuckets(spendQuery, TOP_TENANTS_LIMIT);
    }

    private static SpendKpis rescaleKpis(SpendKpis raw) {
        return new SpendKpis(
                rescale(raw.todayPlatformCost()),
                rescale(raw.todayByokCost()),
                rescale(raw.todayUnknownCost()),
                rescale(raw.sevenDayPlatformCost()),
                rescale(raw.sevenDayByokCost()),
                rescale(raw.sevenDayUnknownCost()),
                rescale(raw.thirtyDayPlatformCost()),
                rescale(raw.thirtyDayByokCost()),
                rescale(raw.thirtyDayUnknownCost()),
                raw.todayCallCount(),
                raw.sevenDayCallCount(),
                raw.thirtyDayCallCount());
    }

    private static List<ProviderStackBarRow> rescaleStackBar(List<ProviderStackBarRow> rows) {
        return rows.stream()
                .map(
                        row ->
                                new ProviderStackBarRow(
                                        row.bucketDate(),
                                        row.provider(),
                                        rescale(row.platformCost()),
                                        rescale(row.byokCost()),
                                        rescale(row.unknownCost()),
                                        row.callCount()))
                .toList();
    }

    private static List<FeatureDonutSlice> computeDonut(List<FeatureSpendBucket> buckets) {
        BigDecimal totalCost =
                buckets.stream()
                        .map(FeatureSpendBucket::totalCost)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalCost.signum() == 0) {
            return buckets.stream()
                    .map(
                            bucket ->
                                    new FeatureDonutSlice(
                                            bucket.feature(),
                                            rescale(bucket.totalCost()),
                                            bucket.callCount(),
                                            0.0))
                    .toList();
        }
        return buckets.stream()
                .map(
                        bucket -> {
                            double percent =
                                    bucket.totalCost()
                                            .multiply(BigDecimal.valueOf(100))
                                            .divide(totalCost, 4, RoundingMode.HALF_UP)
                                            .doubleValue();
                            return new FeatureDonutSlice(
                                    bucket.feature(),
                                    rescale(bucket.totalCost()),
                                    bucket.callCount(),
                                    percent);
                        })
                .toList();
    }

    /**
     * K-anonymity collapse: rows with null tenant_id (deleted/orphan) are aggregated into a single
     * rollup; if the aggregated count is below {@code kAnonymityThreshold} the rollup is labelled
     * "K-anonymized aggregate" instead of "[deleted]" so the UI does not falsely imply deletion.
     */
    private List<TopTenantRow> applyKAnonymity(List<TenantSpendBucket> buckets) {
        List<TopTenantRow> active = new ArrayList<>();
        List<TenantSpendBucket> deletedAndSmall = new ArrayList<>();
        for (TenantSpendBucket bucket : buckets) {
            if (bucket.tenantId() == null || bucket.gmailAccountEmail() == null) {
                deletedAndSmall.add(bucket);
            } else {
                active.add(
                        new TopTenantRow(
                                bucket.tenantId(),
                                bucket.gmailAccountEmail(),
                                rescale(bucket.totalCost()),
                                rescale(bucket.unknownCost()),
                                bucket.callCount(),
                                false,
                                computeUnknownPercentForRow(
                                        bucket.totalCost(), bucket.unknownCost())));
            }
        }
        if (deletedAndSmall.isEmpty()) {
            return List.copyOf(active);
        }
        BigDecimal aggregatedCost =
                deletedAndSmall.stream()
                        .map(TenantSpendBucket::totalCost)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal aggregatedUnknown =
                deletedAndSmall.stream()
                        .map(TenantSpendBucket::unknownCost)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        int aggregatedCalls = deletedAndSmall.stream().mapToInt(TenantSpendBucket::callCount).sum();
        boolean smallBucket = deletedAndSmall.size() < kAnonymityThreshold;
        String placeholder =
                smallBucket ? K_ANONYMIZED_AGGREGATE_PLACEHOLDER : DELETED_ROLLUP_PLACEHOLDER;
        TopTenantRow rollup =
                new TopTenantRow(
                        null,
                        placeholder + " (" + deletedAndSmall.size() + " tenants)",
                        rescale(aggregatedCost),
                        rescale(aggregatedUnknown),
                        aggregatedCalls,
                        true,
                        computeUnknownPercentForRow(aggregatedCost, aggregatedUnknown));
        List<TopTenantRow> result = new ArrayList<>(active.size() + 1);
        result.addAll(active);
        result.add(rollup);
        return List.copyOf(result);
    }

    private static double computeUnknownPercentForRow(
            BigDecimal totalCost, BigDecimal unknownCost) {
        if (totalCost == null || totalCost.signum() == 0) {
            return 0.0;
        }
        return unknownCost
                .multiply(BigDecimal.valueOf(100))
                .divide(totalCost, 4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static double computeUnknownPercent(List<ProviderStackBarRow> stackBar) {
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal unknownCost = BigDecimal.ZERO;
        for (ProviderStackBarRow row : stackBar) {
            totalCost =
                    totalCost.add(row.platformCost()).add(row.byokCost()).add(row.unknownCost());
            unknownCost = unknownCost.add(row.unknownCost());
        }
        if (totalCost.signum() == 0) {
            return 0.0;
        }
        return unknownCost
                .multiply(BigDecimal.valueOf(100))
                .divide(totalCost, 4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static BigDecimal rescale(BigDecimal value) {
        if (value == null) {
            return MONEY_SCALE_ZERO;
        }
        return value.setScale(6, RoundingMode.HALF_UP);
    }
}
