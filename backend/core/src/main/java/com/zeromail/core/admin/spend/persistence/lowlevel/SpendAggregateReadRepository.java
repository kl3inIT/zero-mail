package com.zeromail.core.admin.spend.persistence.lowlevel;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.spend.projection.FeatureSpendBucket;
import com.zeromail.core.admin.spend.projection.ProviderStackBarRow;
import com.zeromail.core.admin.spend.projection.SpendCsvRow;
import com.zeromail.core.admin.spend.projection.SpendKpis;
import com.zeromail.core.admin.spend.projection.SpendQuery;
import com.zeromail.core.admin.spend.projection.TenantSpendBucket;
import com.zeromail.core.llm.domain.LlmProvider;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Raw JDBC read access to {@code llm_call_audit} for the admin spend dashboard.
 *
 * <p><b>Privacy invariant (OPS-SPEND-01/02 + T-08-50 + ARCH-11):</b> every SELECT list is
 * explicitly enumerated and reads ONLY metadata columns. Forbidden content columns (LLM
 * input/output text, request/response payloads) DO NOT EXIST on this table; the projection records
 * carry no body-shaped fields; the runtime SQL spy ({@code SpendAggregateQueryServiceSqlSpyTest})
 * catches regression at runtime; an ArchUnit gate (see {@code
 * backend/core/src/test/java/com/zeromail/core/admin/arch}) catches regression at compile.
 *
 * <p>Query timeout (R-8F-H7): all queries set a 15-second timeout so 90-day range queries do not
 * exceed the operator HTTP timeout budget.
 */
@Repository
public class SpendAggregateReadRepository {

    private static final int QUERY_TIMEOUT_SECONDS = 15;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public SpendAggregateReadRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate =
                Objects.requireNonNull(
                        namedParameterJdbcTemplate, "namedParameterJdbcTemplate must not be null");
        namedParameterJdbcTemplate.getJdbcTemplate().setQueryTimeout(QUERY_TIMEOUT_SECONDS);
    }

    /**
     * KPI tile values for today / 7d / 30d windows. Read from a single aggregation over the last 30
     * days; values for the shorter windows are derived by FILTER-ing on the appropriate cutoff.
     */
    public SpendKpis findKpis(
            Instant todayCutoff, Instant sevenDayCutoff, Instant thirtyDayCutoff) {
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
                                resultSet.getBigDecimal("today_platform"),
                                resultSet.getBigDecimal("today_byok"),
                                resultSet.getBigDecimal("today_unknown"),
                                resultSet.getBigDecimal("seven_platform"),
                                resultSet.getBigDecimal("seven_byok"),
                                resultSet.getBigDecimal("seven_unknown"),
                                resultSet.getBigDecimal("thirty_platform"),
                                resultSet.getBigDecimal("thirty_byok"),
                                resultSet.getBigDecimal("thirty_unknown"),
                                resultSet.getInt("today_calls"),
                                resultSet.getInt("seven_calls"),
                                resultSet.getInt("thirty_calls")));
    }

    public java.util.List<ProviderStackBarRow> findProviderStackBarRows(SpendQuery spendQuery) {
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
        appendOptionalFilters(spendQuery, sql, parameters, null);
        sql.append(
                """
                GROUP BY date_trunc('day', created_at), provider, credential_source
                ORDER BY bucket_date ASC, provider ASC
                """);

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

    public java.util.List<FeatureSpendBucket> findFeatureBuckets(SpendQuery spendQuery) {
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
        appendOptionalFilters(spendQuery, sql, parameters, null);
        sql.append(
                """
                GROUP BY feature
                ORDER BY cost DESC
                """);
        return namedParameterJdbcTemplate.query(
                sql.toString(),
                parameters,
                (resultSet, _) ->
                        new FeatureSpendBucket(
                                resultSet.getString("feature"),
                                resultSet.getBigDecimal("cost"),
                                resultSet.getInt("call_count")));
    }

    public java.util.List<TenantSpendBucket> findTopTenantBuckets(
            SpendQuery spendQuery, int limit) {
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
        appendOptionalFilters(spendQuery, sql, parameters, "lca");
        sql.append(
                """
                GROUP BY lca.tenant_id, gc.google_email
                ORDER BY total_cost DESC
                LIMIT :top_limit
                """);
        parameters.addValue("top_limit", limit);

        return namedParameterJdbcTemplate.query(
                sql.toString(),
                parameters,
                (resultSet, _) ->
                        new TenantSpendBucket(
                                (UUID) resultSet.getObject("tenant_id"),
                                resultSet.getString("gmail_account_email"),
                                resultSet.getBigDecimal("total_cost"),
                                resultSet.getBigDecimal("unknown_cost"),
                                resultSet.getInt("call_count")));
    }

    public int estimateCsvGroupCount(SpendQuery spendQuery) {
        MapSqlParameterSource parameters = baseRangeParameters(spendQuery);
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT COUNT(*)::int FROM (
                            SELECT 1 FROM llm_call_audit
                            WHERE created_at >= :from AND created_at < :to
                        """);
        appendOptionalFilters(spendQuery, sql, parameters, null);
        sql.append(
                """
                        GROUP BY date_trunc('day', created_at), provider, feature, credential_source
                        ) AS counted
                """);
        Integer count =
                namedParameterJdbcTemplate.queryForObject(
                        sql.toString(), parameters, Integer.class);
        return count == null ? 0 : count;
    }

    public java.util.List<SpendCsvRow> findCsvRows(SpendQuery spendQuery, int rowLimit) {
        MapSqlParameterSource parameters =
                baseRangeParameters(spendQuery).addValue("row_limit", rowLimit);
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT date_trunc('day', created_at) AS bucket_date,
                               provider,
                               feature,
                               credential_source,
                               SUM(total_cost_usd) AS total_cost,
                               COUNT(*)::int AS call_count
                        FROM llm_call_audit
                        WHERE created_at >= :from AND created_at < :to
                        """);
        appendOptionalFilters(spendQuery, sql, parameters, null);
        sql.append(
                """
                GROUP BY date_trunc('day', created_at), provider, feature, credential_source
                ORDER BY bucket_date ASC, provider ASC, feature ASC, credential_source ASC
                LIMIT :row_limit
                """);
        return namedParameterJdbcTemplate.query(
                sql.toString(),
                parameters,
                (resultSet, _) ->
                        new SpendCsvRow(
                                resultSet.getTimestamp("bucket_date").toInstant(),
                                resultSet.getString("provider"),
                                resultSet.getString("feature"),
                                resultSet.getString("credential_source"),
                                resultSet.getBigDecimal("total_cost"),
                                resultSet.getInt("call_count")));
    }

    private static MapSqlParameterSource baseRangeParameters(SpendQuery spendQuery) {
        return new MapSqlParameterSource()
                .addValue("from", Timestamp.from(spendQuery.from()))
                .addValue("to", Timestamp.from(spendQuery.to()));
    }

    private static void appendOptionalFilters(
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
                    bucketDate, provider, platformCost, byokCost, unknownCost, callCount);
        }
    }
}
