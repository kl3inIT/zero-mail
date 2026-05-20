package com.zeromail.core.admin.spend.usecases;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.spend.projection.FeatureDonutSlice;
import com.zeromail.core.admin.spend.projection.ProviderStackBarRow;
import com.zeromail.core.admin.spend.projection.SpendDashboardSnapshot;
import com.zeromail.core.admin.spend.projection.SpendKpis;
import com.zeromail.core.admin.spend.projection.SpendQuery;
import com.zeromail.core.admin.spend.projection.TopTenantRow;
import com.zeromail.core.config.ZeroMailCoreProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregate read service over {@code llm_call_audit} for the /admin/spend dashboard.
 *
 * <p><b>Privacy invariant (OPS-SPEND-01/02 + T-08-50 + ARCH-11):</b> every SELECT list in this
 * service is explicitly enumerated and reads ONLY metadata columns (id, tenant_id, provider,
 * feature, model_id, credential_source, prompt_tokens, completion_tokens, total_cost_usd,
 * created_at). The forbidden columns (prompt text, completion text, request body, response body) DO
 * NOT EXIST on this table; the projection records carry no body-shaped fields; the runtime SQL spy
 * ({@code SpendAggregateQueryServiceSqlSpyTest}) catches regression at runtime; the ArchUnit gate
 * ({@code AdminSpendPromptAccessorBanTest}) catches regression at compile.
 *
 * <p>K-anonymity (R-8F-H6, default k = 5): top-tenants buckets with fewer than k tenants collapse
 * to a single "Deleted (k aggregated)" / "K-anonymized aggregate" rollup row.
 *
 * <p>Platform-vs-BYOK classification (R-8F-H1): each row in {@code llm_call_audit} carries a {@code
 * credential_source} column with values {@code PLATFORM | BYOK | UNKNOWN}. The aggregation SUMs
 * THREE buckets per slice. UNKNOWN is reserved for historical rows predating the row-level
 * classification rollout (R-8F-H9).
 *
 * <p>Query timeout (R-8F-H7): all queries set a 15-second timeout via {@code
 * NamedParameterJdbcTemplate.getJdbcTemplate().setQueryTimeout(15)} at construction so 90-day range
 * queries do not exceed the operator HTTP timeout budget.
 */
@Service
public class SpendAggregateQueryService {

    private static final int QUERY_TIMEOUT_SECONDS = 15;
    private static final int TOP_TENANTS_LIMIT = 20;
    private static final BigDecimal MONEY_SCALE_RESULT = BigDecimal.ZERO.setScale(6);
    private static final String K_ANONYMITY_FOOTER_NOTE =
            "Per-tenant spend bucketed by 7-day k-anonymity (k≥5)."
                    + " Exact per-tenant cost is not exposed.";
    private static final String DELETED_ROLLUP_PLACEHOLDER = "[deleted]";
    private static final String K_ANONYMIZED_AGGREGATE_PLACEHOLDER = "K-anonymized aggregate";

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final Clock clock;
    private final int kAnonymityThreshold;

    public SpendAggregateQueryService(
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            Clock clock,
            ZeroMailCoreProperties coreProperties) {
        this.namedParameterJdbcTemplate =
                Objects.requireNonNull(
                        namedParameterJdbcTemplate, "namedParameterJdbcTemplate must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(coreProperties, "coreProperties must not be null");
        this.kAnonymityThreshold = coreProperties.admin().spend().kAnonymityThreshold();
        namedParameterJdbcTemplate.getJdbcTemplate().setQueryTimeout(QUERY_TIMEOUT_SECONDS);
    }

    @Transactional(readOnly = true)
    public SpendDashboardSnapshot snapshot(SpendQuery spendQuery) {
        Objects.requireNonNull(spendQuery, "spendQuery must not be null");
        Instant now = clock.instant();
        SpendKpis kpis = queryKpis(now);
        List<ProviderStackBarRow> stackBar = queryProviderStackBar(spendQuery);
        List<FeatureDonutSlice> donut = queryFeatureDonut(spendQuery);
        List<TopTenantRow> topTenants = queryTopTenants(spendQuery);
        double unknownPercent = computeUnknownPercent(stackBar);
        return new SpendDashboardSnapshot(
                kpis, stackBar, donut, topTenants, now, K_ANONYMITY_FOOTER_NOTE, unknownPercent);
    }

    /**
     * KPI tile values for today / 7d / 30d windows. Read from a single aggregation over the last 30
     * days; values for the shorter windows are derived by filtering created_at against the
     * appropriate cutoff.
     */
    private SpendKpis queryKpis(Instant now) {
        Instant todayCutoff = now.minus(Duration.ofDays(1));
        Instant sevenDayCutoff = now.minus(Duration.ofDays(7));
        Instant thirtyDayCutoff = now.minus(Duration.ofDays(30));
        MapSqlParameterSource parameters =
                new MapSqlParameterSource()
                        .addValue("today_cutoff", Timestamp.from(todayCutoff))
                        .addValue("seven_day_cutoff", Timestamp.from(sevenDayCutoff))
                        .addValue("thirty_day_cutoff", Timestamp.from(thirtyDayCutoff));
        return namedParameterJdbcTemplate.queryForObject(
                """
                SELECT
                    COALESCE(SUM(total_cost_usd) FILTER (
                        WHERE credential_source = 'PLATFORM'
                          AND created_at >= :today_cutoff), 0) AS today_platform,
                    COALESCE(SUM(total_cost_usd) FILTER (
                        WHERE credential_source = 'BYOK'
                          AND created_at >= :today_cutoff), 0) AS today_byok,
                    COALESCE(SUM(total_cost_usd) FILTER (
                        WHERE credential_source = 'UNKNOWN'
                          AND created_at >= :today_cutoff), 0) AS today_unknown,
                    COALESCE(SUM(total_cost_usd) FILTER (
                        WHERE credential_source = 'PLATFORM'
                          AND created_at >= :seven_day_cutoff), 0) AS seven_platform,
                    COALESCE(SUM(total_cost_usd) FILTER (
                        WHERE credential_source = 'BYOK'
                          AND created_at >= :seven_day_cutoff), 0) AS seven_byok,
                    COALESCE(SUM(total_cost_usd) FILTER (
                        WHERE credential_source = 'UNKNOWN'
                          AND created_at >= :seven_day_cutoff), 0) AS seven_unknown,
                    COALESCE(SUM(total_cost_usd) FILTER (
                        WHERE credential_source = 'PLATFORM'
                          AND created_at >= :thirty_day_cutoff), 0) AS thirty_platform,
                    COALESCE(SUM(total_cost_usd) FILTER (
                        WHERE credential_source = 'BYOK'
                          AND created_at >= :thirty_day_cutoff), 0) AS thirty_byok,
                    COALESCE(SUM(total_cost_usd) FILTER (
                        WHERE credential_source = 'UNKNOWN'
                          AND created_at >= :thirty_day_cutoff), 0) AS thirty_unknown,
                    COUNT(*) FILTER (
                        WHERE created_at >= :today_cutoff)::int AS today_calls,
                    COUNT(*) FILTER (
                        WHERE created_at >= :seven_day_cutoff)::int AS seven_calls,
                    COUNT(*) FILTER (
                        WHERE created_at >= :thirty_day_cutoff)::int AS thirty_calls
                FROM llm_call_audit
                WHERE created_at >= :thirty_day_cutoff
                """,
                parameters,
                (resultSet, _) ->
                        new SpendKpis(
                                rescale(resultSet.getBigDecimal("today_platform")),
                                rescale(resultSet.getBigDecimal("today_byok")),
                                rescale(resultSet.getBigDecimal("today_unknown")),
                                rescale(resultSet.getBigDecimal("seven_platform")),
                                rescale(resultSet.getBigDecimal("seven_byok")),
                                rescale(resultSet.getBigDecimal("seven_unknown")),
                                rescale(resultSet.getBigDecimal("thirty_platform")),
                                rescale(resultSet.getBigDecimal("thirty_byok")),
                                rescale(resultSet.getBigDecimal("thirty_unknown")),
                                resultSet.getInt("today_calls"),
                                resultSet.getInt("seven_calls"),
                                resultSet.getInt("thirty_calls")));
    }

    private List<ProviderStackBarRow> queryProviderStackBar(SpendQuery spendQuery) {
        MapSqlParameterSource parameters = baseRangeParameters(spendQuery);
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT date_trunc('day', created_at) AS bucket_date,
                               provider,
                               credential_source,
                               SUM(total_cost_usd) AS cost,
                               COUNT(*)::int AS call_count
                        FROM llm_call_audit
                        WHERE created_at >= :from AND created_at < :to
                        """);
        appendOptionalFilters(spendQuery, sql, parameters);
        sql.append(
                """
                GROUP BY date_trunc('day', created_at), provider, credential_source
                ORDER BY bucket_date ASC, provider ASC
                """);

        // Intermediate grouping: (bucketDate, provider) -> 3-bucket cost map + total calls.
        Map<String, BarAccumulator> grouped = new HashMap<>();
        namedParameterJdbcTemplate.query(
                sql.toString(),
                parameters,
                resultSet -> {
                    Timestamp bucketDate = resultSet.getTimestamp("bucket_date");
                    String provider = resultSet.getString("provider");
                    String credentialSource = resultSet.getString("credential_source");
                    BigDecimal cost = resultSet.getBigDecimal("cost");
                    int callCount = resultSet.getInt("call_count");
                    String key = bucketDate.toInstant() + "|" + provider;
                    BarAccumulator accumulator =
                            grouped.computeIfAbsent(
                                    key, _ -> new BarAccumulator(bucketDate.toInstant(), provider));
                    accumulator.addCredentialBucket(credentialSource, cost, callCount);
                });

        return grouped.values().stream()
                .map(BarAccumulator::toRow)
                .sorted(
                        (left, right) -> {
                            int byDate = left.bucketDate().compareTo(right.bucketDate());
                            return byDate != 0
                                    ? byDate
                                    : left.provider().compareTo(right.provider());
                        })
                .toList();
    }

    private List<FeatureDonutSlice> queryFeatureDonut(SpendQuery spendQuery) {
        MapSqlParameterSource parameters = baseRangeParameters(spendQuery);
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT feature,
                               SUM(total_cost_usd) AS cost,
                               COUNT(*)::int AS call_count
                        FROM llm_call_audit
                        WHERE created_at >= :from AND created_at < :to
                        """);
        appendOptionalFilters(spendQuery, sql, parameters);
        sql.append(
                """
                GROUP BY feature
                ORDER BY cost DESC
                """);

        List<FeatureBucket> buckets =
                namedParameterJdbcTemplate.query(
                        sql.toString(),
                        parameters,
                        (resultSet, _) ->
                                new FeatureBucket(
                                        resultSet.getString("feature"),
                                        rescale(resultSet.getBigDecimal("cost")),
                                        resultSet.getInt("call_count")));
        BigDecimal totalCost =
                buckets.stream().map(FeatureBucket::cost).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalCost.signum() == 0) {
            return buckets.stream()
                    .map(
                            bucket ->
                                    new FeatureDonutSlice(
                                            bucket.feature, bucket.cost, bucket.calls, 0.0))
                    .toList();
        }
        return buckets.stream()
                .map(
                        bucket -> {
                            double percent =
                                    bucket.cost
                                            .multiply(BigDecimal.valueOf(100))
                                            .divide(totalCost, 4, RoundingMode.HALF_UP)
                                            .doubleValue();
                            return new FeatureDonutSlice(
                                    bucket.feature, bucket.cost, bucket.calls, percent);
                        })
                .toList();
    }

    private List<TopTenantRow> queryTopTenants(SpendQuery spendQuery) {
        MapSqlParameterSource parameters = baseRangeParameters(spendQuery);
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT lca.tenant_id AS tenant_id,
                               gc.google_email AS gmail_account_email,
                               SUM(lca.total_cost_usd) AS total_cost,
                               COALESCE(SUM(lca.total_cost_usd) FILTER (
                                   WHERE lca.credential_source = 'UNKNOWN'), 0) AS unknown_cost,
                               COUNT(*)::int AS call_count
                        FROM llm_call_audit lca
                        LEFT JOIN tenants t ON t.id = lca.tenant_id
                        LEFT JOIN gmail_connections gc ON gc.tenant_id = t.id
                        WHERE lca.created_at >= :from AND lca.created_at < :to
                        """);
        appendOptionalFiltersForAlias(spendQuery, sql, parameters, "lca");
        sql.append(
                """
                GROUP BY lca.tenant_id, gc.google_email
                ORDER BY total_cost DESC
                LIMIT :top_limit
                """);
        parameters.addValue("top_limit", TOP_TENANTS_LIMIT);

        List<TenantBucket> rawRows =
                namedParameterJdbcTemplate.query(
                        sql.toString(),
                        parameters,
                        (resultSet, _) ->
                                new TenantBucket(
                                        (UUID) resultSet.getObject("tenant_id"),
                                        resultSet.getString("gmail_account_email"),
                                        rescale(resultSet.getBigDecimal("total_cost")),
                                        rescale(resultSet.getBigDecimal("unknown_cost")),
                                        resultSet.getInt("call_count")));

        return applyKAnonymity(rawRows);
    }

    /**
     * K-anonymity collapse: rows with NULL tenant_id (deleted tenants) AND any group with fewer
     * than {@code kAnonymityThreshold} distinct tenants get rolled up into a single rollup entry.
     * The active-tenant rows above k stay as-is.
     */
    private List<TopTenantRow> applyKAnonymity(List<TenantBucket> rawRows) {
        List<TopTenantRow> active = new ArrayList<>();
        List<TenantBucket> deletedAndSmall = new ArrayList<>();
        for (TenantBucket bucket : rawRows) {
            if (bucket.tenantId == null || bucket.gmailAccountEmail == null) {
                // Deleted tenant (FK to tenants is broken/cascade or gmail_connection missing).
                deletedAndSmall.add(bucket);
            } else {
                active.add(
                        new TopTenantRow(
                                bucket.tenantId,
                                bucket.gmailAccountEmail,
                                bucket.totalCost,
                                bucket.unknownCost,
                                bucket.calls,
                                false,
                                computeUnknownPercentForRow(bucket.totalCost, bucket.unknownCost)));
            }
        }
        if (deletedAndSmall.isEmpty()) {
            return List.copyOf(active);
        }
        if (deletedAndSmall.size() >= kAnonymityThreshold) {
            // Enough deleted tenants — still collapse for k-anonymity (no per-tenant view of
            // deleted-tenant breakdown is exposed regardless).
        }
        BigDecimal aggregatedCost =
                deletedAndSmall.stream()
                        .map(TenantBucket::totalCost)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal aggregatedUnknown =
                deletedAndSmall.stream()
                        .map(TenantBucket::unknownCost)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        int aggregatedCalls = deletedAndSmall.stream().mapToInt(TenantBucket::calls).sum();
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

    private static MapSqlParameterSource baseRangeParameters(SpendQuery spendQuery) {
        return new MapSqlParameterSource()
                .addValue("from", Timestamp.from(spendQuery.from()))
                .addValue("to", Timestamp.from(spendQuery.to()));
    }

    private static void appendOptionalFilters(
            SpendQuery spendQuery, StringBuilder sql, MapSqlParameterSource parameters) {
        appendOptionalFiltersForAlias(spendQuery, sql, parameters, null);
    }

    private static void appendOptionalFiltersForAlias(
            SpendQuery spendQuery,
            StringBuilder sql,
            MapSqlParameterSource parameters,
            String alias) {
        String prefix = alias == null ? "" : alias + ".";
        spendQuery
                .providers()
                .filter(set -> !set.isEmpty())
                .ifPresent(
                        providerSet -> {
                            sql.append(" AND ").append(prefix).append("provider IN (:providers) ");
                            parameters.addValue(
                                    "providers",
                                    providerSet.stream()
                                            .map(LlmProvider::name)
                                            .collect(Collectors.toSet()));
                        });
        spendQuery
                .features()
                .filter(set -> !set.isEmpty())
                .ifPresent(
                        featureSet -> {
                            sql.append(" AND ").append(prefix).append("feature IN (:features) ");
                            parameters.addValue(
                                    "features",
                                    featureSet.stream()
                                            .map(Feature::name)
                                            .collect(Collectors.toSet()));
                        });
    }

    private static BigDecimal rescale(BigDecimal value) {
        if (value == null) {
            return MONEY_SCALE_RESULT;
        }
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    /** Mutable accumulator for the (bucketDate, provider) GROUP key. */
    private static final class BarAccumulator {
        private final Instant bucketDate;
        private final String provider;
        private BigDecimal platformCost = BigDecimal.ZERO;
        private BigDecimal byokCost = BigDecimal.ZERO;
        private BigDecimal unknownCost = BigDecimal.ZERO;
        private int callCount = 0;

        private BarAccumulator(Instant bucketDate, String provider) {
            this.bucketDate = bucketDate;
            this.provider = provider;
        }

        private void addCredentialBucket(String credentialSource, BigDecimal cost, int calls) {
            BigDecimal safeCost = cost == null ? BigDecimal.ZERO : cost;
            switch (credentialSource) {
                case "PLATFORM" -> platformCost = platformCost.add(safeCost);
                case "BYOK" -> byokCost = byokCost.add(safeCost);
                case "UNKNOWN" -> unknownCost = unknownCost.add(safeCost);
                default ->
                        throw new IllegalStateException(
                                "Unexpected credential_source value: " + credentialSource);
            }
            callCount += calls;
        }

        private ProviderStackBarRow toRow() {
            return new ProviderStackBarRow(
                    bucketDate,
                    provider,
                    rescale(platformCost),
                    rescale(byokCost),
                    rescale(unknownCost),
                    callCount);
        }
    }

    private record FeatureBucket(String feature, BigDecimal cost, int calls) {}

    private record TenantBucket(
            UUID tenantId,
            String gmailAccountEmail,
            BigDecimal totalCost,
            BigDecimal unknownCost,
            int calls) {}

    // Filtering by provider/feature set in plain Java for the K-anonymity helper signature.
    @SuppressWarnings("unused")
    private static Set<String> namesOf(Set<? extends Enum<?>> set) {
        return set.stream().map(Enum::name).collect(Collectors.toSet());
    }
}
