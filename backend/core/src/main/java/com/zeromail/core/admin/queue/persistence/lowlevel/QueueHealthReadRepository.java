package com.zeromail.core.admin.queue.persistence.lowlevel;

import com.zeromail.core.admin.queue.projection.DeadLetterRow;
import com.zeromail.core.admin.queue.projection.QueueDepthByType;
import com.zeromail.core.admin.queue.projection.RetryDistributionBucket;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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

    public double findFailureRateLast24h() {
        Double rate =
                namedParameterJdbcTemplate.queryForObject(
                        """
                        SELECT (
                            COUNT(*) FILTER (
                                WHERE status = 'FAILED'
                                  AND last_failed_at >= NOW() - INTERVAL '24 hours'
                            )::double precision
                            / NULLIF(
                                COUNT(*) FILTER (
                                    WHERE created_at >= NOW() - INTERVAL '24 hours'
                                ),
                                0
                            )
                        ) AS failure_rate
                        FROM processing_job
                        """,
                        new MapSqlParameterSource(),
                        Double.class);
        return rate == null ? 0.0 : rate;
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

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
