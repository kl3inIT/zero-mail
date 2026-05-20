package com.zeromail.core.admin.spend.usecases;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.spend.projection.SpendQuery;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Streaming CSV exporter for the /admin/spend dashboard. Per R-8F-H5, the exporter runs a pre-query
 * row-count estimate before streaming starts; if the estimate exceeds {@link #MAX_ROWS}, the export
 * is rejected with {@code error.admin.spend_export_too_large} BEFORE any bytes are written. No
 * partial-CSV failure mode.
 *
 * <p>Allowed columns (T-08-56): bucketDate, provider, feature, credentialSource, platformCost,
 * byokCost, unknownCost, callCount. NEVER includes tenant_id, prompt text, completion text, or any
 * content-shaped field.
 */
@Service
public class SpendCsvExporter {

    /** Maximum rows the exporter will stream — protects against a 90-day full-table dump. */
    public static final int MAX_ROWS = 10_000;

    private static final String[] HEADER_COLUMNS = {
        "bucketDate", "provider", "feature", "credentialSource", "totalCost", "callCount"
    };

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public SpendCsvExporter(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate =
                Objects.requireNonNull(
                        namedParameterJdbcTemplate, "namedParameterJdbcTemplate must not be null");
    }

    /**
     * Estimate the result-row count before streaming. Throws {@link IllegalArgumentException} with
     * the {@code error.admin.spend_export_too_large} marker when over {@link #MAX_ROWS}.
     */
    @Transactional(readOnly = true)
    public int estimateRowCount(SpendQuery spendQuery) {
        Objects.requireNonNull(spendQuery, "spendQuery must not be null");
        MapSqlParameterSource parameters =
                new MapSqlParameterSource()
                        .addValue("from", Timestamp.from(spendQuery.from()))
                        .addValue("to", Timestamp.from(spendQuery.to()));
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT COUNT(*)::int FROM (
                            SELECT 1 FROM llm_call_audit
                            WHERE created_at >= :from AND created_at < :to
                        """);
        appendOptionalFilters(spendQuery, sql, parameters);
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

    @Transactional(readOnly = true)
    public void streamCsv(SpendQuery spendQuery, OutputStream outputStream) throws IOException {
        Objects.requireNonNull(spendQuery, "spendQuery must not be null");
        Objects.requireNonNull(outputStream, "outputStream must not be null");
        int estimate = estimateRowCount(spendQuery);
        if (estimate > MAX_ROWS) {
            throw new IllegalArgumentException(
                    "error.admin.spend_export_too_large: estimated "
                            + estimate
                            + " rows exceeds maximum "
                            + MAX_ROWS);
        }

        Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        writeRow(writer, HEADER_COLUMNS);

        MapSqlParameterSource parameters =
                new MapSqlParameterSource()
                        .addValue("from", Timestamp.from(spendQuery.from()))
                        .addValue("to", Timestamp.from(spendQuery.to()))
                        .addValue("row_limit", MAX_ROWS);
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
        appendOptionalFilters(spendQuery, sql, parameters);
        sql.append(
                """
                GROUP BY date_trunc('day', created_at), provider, feature, credential_source
                ORDER BY bucket_date ASC, provider ASC, feature ASC, credential_source ASC
                LIMIT :row_limit
                """);

        namedParameterJdbcTemplate.query(
                sql.toString(),
                parameters,
                resultSet -> {
                    Instant bucketDate = resultSet.getTimestamp("bucket_date").toInstant();
                    String provider = resultSet.getString("provider");
                    String feature = resultSet.getString("feature");
                    String credentialSource = resultSet.getString("credential_source");
                    BigDecimal totalCost =
                            resultSet.getBigDecimal("total_cost").setScale(6, RoundingMode.HALF_UP);
                    int callCount = resultSet.getInt("call_count");
                    try {
                        writeRow(
                                writer,
                                bucketDate.toString(),
                                provider,
                                feature,
                                credentialSource,
                                totalCost.toPlainString(),
                                Integer.toString(callCount));
                    } catch (IOException ioException) {
                        throw new RuntimeException(ioException);
                    }
                });
        writer.flush();
    }

    private static void writeRow(Writer writer, String... columns) throws IOException {
        for (int columnIndex = 0; columnIndex < columns.length; columnIndex++) {
            if (columnIndex > 0) {
                writer.write(',');
            }
            writer.write(escape(columns[columnIndex]));
        }
        writer.write('\n');
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static void appendOptionalFilters(
            SpendQuery spendQuery, StringBuilder sql, MapSqlParameterSource parameters) {
        spendQuery
                .providers()
                .filter(set -> !set.isEmpty())
                .ifPresent(
                        providerSet -> {
                            sql.append(" AND provider IN (:providers) ");
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
                            sql.append(" AND feature IN (:features) ");
                            parameters.addValue(
                                    "features",
                                    featureSet.stream()
                                            .map(Feature::name)
                                            .collect(Collectors.toSet()));
                        });
    }
}
