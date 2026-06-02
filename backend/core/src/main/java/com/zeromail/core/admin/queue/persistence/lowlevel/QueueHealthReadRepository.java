package com.zeromail.core.admin.queue.persistence.lowlevel;

import com.zeromail.core.admin.queue.projection.DeadLetterRow;
import com.zeromail.core.admin.queue.projection.FailureWindow24h;
import com.zeromail.core.admin.queue.projection.JobDetail;
import com.zeromail.core.admin.queue.projection.JobRow;
import com.zeromail.core.admin.queue.projection.QueueDepthByType;
import com.zeromail.core.admin.queue.projection.RetryDistributionBucket;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Raw JDBC read access to {@code processing_job} for the admin queue dashboard.
 *
 * <p>Privacy invariant (SPEC OPS-QUEUE-01/02 + T-08-45): every SELECT list omits the stored job
 * body column. {@code QueueHealthQueryServiceSqlSpyTest} gates the emitted SQL at runtime.
 */
@Repository
public class QueueHealthReadRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public QueueHealthReadRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate =
                Objects.requireNonNull(
                        namedParameterJdbcTemplate, "namedParameterJdbcTemplate must not be null");
    }

    public List<QueueDepthByType> findDepthByType() {
        Map<String, int[]> byType = new HashMap<>();
        namedParameterJdbcTemplate.query(
                """
                SELECT job_type, status, COUNT(*)::int AS row_count
                FROM processing_job
                WHERE status IN ('PENDING', 'PROCESSING')
                GROUP BY job_type, status
                """,
                new MapSqlParameterSource(),
                resultSet -> {
                    String jobType = resultSet.getString("job_type");
                    String status = resultSet.getString("status");
                    int rowCount = resultSet.getInt("row_count");
                    int[] counts = byType.computeIfAbsent(jobType, _ -> new int[] {0, 0});
                    if ("PENDING".equals(status)) {
                        counts[0] = rowCount;
                    } else if ("PROCESSING".equals(status)) {
                        counts[1] = rowCount;
                    }
                });
        List<QueueDepthByType> result = new ArrayList<>(byType.size());
        for (Map.Entry<String, int[]> entry : byType.entrySet()) {
            result.add(
                    new QueueDepthByType(entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
        }
        result.sort((left, right) -> left.jobType().compareTo(right.jobType()));
        return result;
    }

    public Instant findOldestUnleasedJobCreatedAt() {
        Timestamp oldestCreatedAt =
                namedParameterJdbcTemplate.queryForObject(
                        """
                        SELECT MIN(created_at) AS oldest
                        FROM processing_job
                        WHERE status = 'PENDING'
                          AND (locked_until IS NULL OR locked_until < NOW())
                        """,
                        new MapSqlParameterSource(),
                        (resultSet, _) -> resultSet.getTimestamp("oldest"));
        return oldestCreatedAt == null ? null : oldestCreatedAt.toInstant();
    }

    public List<RetryDistributionBucket> findRetryHistogram() {
        List<RetryDistributionBucket> buckets =
                namedParameterJdbcTemplate.query(
                        """
                        SELECT LEAST(attempts, 4) AS attempts_bucket, COUNT(*)::int AS row_count
                        FROM processing_job
                        WHERE status IN ('PENDING', 'PROCESSING', 'FAILED', 'DEAD_LETTER')
                        GROUP BY LEAST(attempts, 4)
                        ORDER BY attempts_bucket
                        """,
                        new MapSqlParameterSource(),
                        (resultSet, _) ->
                                new RetryDistributionBucket(
                                        resultSet.getInt("attempts_bucket"),
                                        resultSet.getInt("row_count")));
        Map<Integer, Integer> indexed = new HashMap<>();
        for (RetryDistributionBucket bucket : buckets) {
            indexed.put(bucket.attemptsBucket(), bucket.rowCount());
        }
        List<RetryDistributionBucket> result = new ArrayList<>(5);
        for (int bucketIndex = 0; bucketIndex <= 4; bucketIndex++) {
            result.add(
                    new RetryDistributionBucket(bucketIndex, indexed.getOrDefault(bucketIndex, 0)));
        }
        return result;
    }

    /**
     * 24h-bounded failure window. Returns both the numerator (FAILED rows whose failure landed in
     * the window) and the denominator (rows created in the window) in a single scan so the caller
     * can derive the rate AND expose the raw sample size — the UI suppresses a misleading
     * percentage when {@code sampleSize} is tiny (R-8E-H2 + small-sample guard).
     */
    public FailureWindow24h findFailureWindowLast24h() {
        FailureWindow24h failureWindow =
                namedParameterJdbcTemplate.queryForObject(
                        """
                        SELECT
                            COUNT(*) FILTER (
                                WHERE status = 'FAILED'
                                  AND last_failed_at >= NOW() - INTERVAL '24 hours'
                            )::int AS failed_count,
                            COUNT(*) FILTER (
                                WHERE created_at >= NOW() - INTERVAL '24 hours'
                            )::int AS sample_size
                        FROM processing_job
                        """,
                        new MapSqlParameterSource(),
                        (resultSet, _) ->
                                new FailureWindow24h(
                                        resultSet.getInt("failed_count"),
                                        resultSet.getInt("sample_size")));
        return failureWindow == null ? new FailureWindow24h(0, 0) : failureWindow;
    }

    public int findDeadLetterCount() {
        Integer count =
                namedParameterJdbcTemplate.queryForObject(
                        "SELECT COUNT(*)::int FROM processing_job WHERE status = 'DEAD_LETTER'",
                        new MapSqlParameterSource(),
                        Integer.class);
        return count == null ? 0 : count;
    }

    public int findAdminRequeuedLast24h() {
        Integer count =
                namedParameterJdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)::int
                        FROM processing_job
                        WHERE admin_requeue_count > 0
                          AND last_requeued_at >= NOW() - INTERVAL '24 hours'
                        """,
                        new MapSqlParameterSource(),
                        Integer.class);
        return count == null ? 0 : count;
    }

    public List<DeadLetterRow> findDeadLetterPage(int offset, int fetchSize) {
        MapSqlParameterSource parameters =
                new MapSqlParameterSource().addValue("limit", fetchSize).addValue("offset", offset);
        return namedParameterJdbcTemplate.query(
                """
                SELECT id, job_type, last_failure_reason, attempts,
                       admin_requeue_count, last_failed_at, created_at
                FROM processing_job
                WHERE status = 'DEAD_LETTER'
                ORDER BY last_failed_at DESC NULLS LAST, id DESC
                LIMIT :limit OFFSET :offset
                """,
                parameters,
                (resultSet, _) ->
                        new DeadLetterRow(
                                (UUID) resultSet.getObject("id"),
                                resultSet.getString("job_type"),
                                resultSet.getString("last_failure_reason"),
                                resultSet.getInt("attempts"),
                                resultSet.getInt("admin_requeue_count"),
                                toInstant(resultSet.getTimestamp("last_failed_at")),
                                toInstant(resultSet.getTimestamp("created_at"))));
    }

    /**
     * Unified job list across every {@code processing_job} job type. {@code status} accepts the
     * stored statuses plus the synthetic {@code SCHEDULED} (PENDING with a future {@code
     * next_run_at}); {@code jobType} filters to one type. Both are optional. Never selects {@code
     * payload_json} (gated by {@code QueueHealthQueryServiceSqlSpyTest}).
     */
    public List<JobRow> findJobsPage(String status, String jobType, int offset, int fetchSize) {
        MapSqlParameterSource parameters =
                new MapSqlParameterSource().addValue("limit", fetchSize).addValue("offset", offset);
        String filterClause = buildJobFilter(status, jobType, parameters);
        @SuppressWarnings("SqlSourceToSinkFlow")
        String sql =
                JOB_ROW_SELECT
                        + " FROM processing_job"
                        + filterClause
                        + " ORDER BY updated_at DESC NULLS LAST, id DESC"
                        + " LIMIT :limit OFFSET :offset";
        return namedParameterJdbcTemplate.query(sql, parameters, JOB_ROW_MAPPER);
    }

    public int findJobsCount(String status, String jobType) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        String filterClause = buildJobFilter(status, jobType, parameters);
        @SuppressWarnings("SqlSourceToSinkFlow")
        String sql = "SELECT COUNT(*)::int FROM processing_job" + filterClause;
        Integer count = namedParameterJdbcTemplate.queryForObject(sql, parameters, Integer.class);
        return count == null ? 0 : count;
    }

    public Optional<JobDetail> findJobDetail(UUID jobId) {
        List<JobDetail> jobs =
                namedParameterJdbcTemplate.query(
                        """
                        SELECT id, job_type, status, attempts, next_run_at, created_at, updated_at,
                               started_at, heartbeat_at, completed_at, last_failure_reason,
                               admin_requeue_count, last_requeued_at,
                               (status = 'PENDING' AND next_run_at > NOW()) AS scheduled,
                               CASE WHEN job_type = 'CATALOG_SYNC' THEN 'catalog-sync'
                                    ELSE 'cleanup' END AS source
                        FROM processing_job
                        WHERE id = :jobId
                        """,
                        new MapSqlParameterSource().addValue("jobId", jobId),
                        (resultSet, _) ->
                                new JobDetail(
                                        (UUID) resultSet.getObject("id"),
                                        resultSet.getString("source"),
                                        resultSet.getString("job_type"),
                                        resultSet.getString("status"),
                                        resultSet.getBoolean("scheduled"),
                                        resultSet.getInt("attempts"),
                                        toInstant(resultSet.getTimestamp("next_run_at")),
                                        toInstant(resultSet.getTimestamp("created_at")),
                                        toInstant(resultSet.getTimestamp("updated_at")),
                                        toInstant(resultSet.getTimestamp("started_at")),
                                        toInstant(resultSet.getTimestamp("heartbeat_at")),
                                        toInstant(resultSet.getTimestamp("completed_at")),
                                        resultSet.getString("last_failure_reason"),
                                        resultSet.getInt("admin_requeue_count"),
                                        toInstant(resultSet.getTimestamp("last_requeued_at"))));
        return jobs.stream().findFirst();
    }

    /**
     * Appends an optional {@code status}/{@code jobType} filter to a {@code processing_job} query.
     * The synthetic {@code SCHEDULED} status maps to {@code PENDING AND next_run_at > NOW()}; all
     * other status values are matched literally. Values are bound as named parameters — only the
     * fixed clause fragments are concatenated, never user input.
     */
    private static String buildJobFilter(
            String status, String jobType, MapSqlParameterSource parameters) {
        StringBuilder filter = new StringBuilder(" WHERE 1 = 1");
        if (status != null && !status.isBlank()) {
            if ("SCHEDULED".equals(status)) {
                filter.append(" AND status = 'PENDING' AND next_run_at > NOW()");
            } else {
                filter.append(" AND status = :status");
                parameters.addValue("status", status);
            }
        }
        if (jobType != null && !jobType.isBlank()) {
            filter.append(" AND job_type = :jobType");
            parameters.addValue("jobType", jobType);
        }
        return filter.toString();
    }

    private static final String JOB_ROW_SELECT =
            """
            SELECT id, job_type, status, attempts, next_run_at, created_at, updated_at,
                   (status = 'PENDING' AND next_run_at > NOW()) AS scheduled,
                   CASE WHEN job_type = 'CATALOG_SYNC' THEN 'catalog-sync'
                        ELSE 'cleanup' END AS source""";

    private static final RowMapper<JobRow> JOB_ROW_MAPPER =
            (ResultSet resultSet, int rowNumber) -> mapJobRow(resultSet);

    private static JobRow mapJobRow(ResultSet resultSet) throws SQLException {
        return new JobRow(
                (UUID) resultSet.getObject("id"),
                resultSet.getString("source"),
                resultSet.getString("job_type"),
                resultSet.getString("status"),
                resultSet.getBoolean("scheduled"),
                resultSet.getInt("attempts"),
                toInstant(resultSet.getTimestamp("next_run_at")),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at")));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
