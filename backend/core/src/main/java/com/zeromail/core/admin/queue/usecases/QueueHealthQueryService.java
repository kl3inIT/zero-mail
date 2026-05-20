package com.zeromail.core.admin.queue.usecases;

import com.zeromail.core.admin.queue.projection.DeadLetterPage;
import com.zeromail.core.admin.queue.projection.DeadLetterRow;
import com.zeromail.core.admin.queue.projection.QueueDepthByType;
import com.zeromail.core.admin.queue.projection.QueueHealthSnapshot;
import com.zeromail.core.admin.queue.projection.RetryDistributionBucket;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregate read service over {@code processing_job} for the admin queue health dashboard.
 *
 * <p><b>Privacy invariant (SPEC OPS-QUEUE-01/02 + T-08-45):</b> every SELECT list in this service
 * intentionally omits the stored job body column. {@code QueueHealthQueryServiceSqlSpyTest} gates
 * the emitted SQL at runtime; {@code AdminPathBodyBanTest} gates the projection package at compile
 * time.
 *
 * <p>{@code failureRateLast24h} uses a 24h-bounded denominator (R-8E-H2) so the rate does not
 * asymptote to zero against the lifetime row count.
 *
 * <p>TODO v1.3+: per-job-type failure histogram (R-8E-H5 deferred).
 */
@Service
public class QueueHealthQueryService {

    private static final int DEAD_LETTER_PAGE_DEFAULT_LIMIT = 25;
    private static final int DEAD_LETTER_PAGE_MAX_LIMIT = 100;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final Clock clock;

    public QueueHealthQueryService(
            NamedParameterJdbcTemplate namedParameterJdbcTemplate, Clock clock) {
        this.namedParameterJdbcTemplate =
                Objects.requireNonNull(
                        namedParameterJdbcTemplate, "namedParameterJdbcTemplate must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(readOnly = true)
    public QueueHealthSnapshot snapshot() {
        List<QueueDepthByType> depthByType = queryDepthByType();
        Duration oldestUnleasedJobAge = queryOldestUnleasedJobAge();
        List<RetryDistributionBucket> retryHistogram = queryRetryHistogram();
        double failureRateLast24h = queryFailureRateLast24h();
        int deadLetterCount = queryDeadLetterCount();
        int adminRequeuedLast24h = queryAdminRequeuedLast24h();
        return new QueueHealthSnapshot(
                depthByType,
                oldestUnleasedJobAge,
                retryHistogram,
                failureRateLast24h,
                deadLetterCount,
                adminRequeuedLast24h,
                clock.instant());
    }

    @Transactional(readOnly = true)
    public DeadLetterPage deadLetterPage(String cursor, int limit) {
        int safeLimit = clampLimit(limit);
        int offset = parseCursor(cursor);
        MapSqlParameterSource parameters =
                new MapSqlParameterSource()
                        .addValue("limit", safeLimit + 1)
                        .addValue("offset", offset);

        // Explicit SELECT list — NEVER includes the stored body column. The projection record
        // has no body-shaped field as a defense-in-depth gate (AdminPathBodyBanTest).
        List<DeadLetterRow> rows =
                namedParameterJdbcTemplate.query(
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
                                        (java.util.UUID) resultSet.getObject("id"),
                                        resultSet.getString("job_type"),
                                        resultSet.getString("last_failure_reason"),
                                        resultSet.getInt("attempts"),
                                        resultSet.getInt("admin_requeue_count"),
                                        toInstant(resultSet.getTimestamp("last_failed_at")),
                                        toInstant(resultSet.getTimestamp("created_at"))));

        boolean hasNextPage = rows.size() > safeLimit;
        List<DeadLetterRow> visibleRows = hasNextPage ? rows.subList(0, safeLimit) : rows;
        Integer totalEstimate =
                namedParameterJdbcTemplate.queryForObject(
                        "SELECT COUNT(*)::int FROM processing_job WHERE status = 'DEAD_LETTER'",
                        new MapSqlParameterSource(),
                        Integer.class);
        String nextCursor = hasNextPage ? String.valueOf(offset + safeLimit) : null;
        return new DeadLetterPage(
                visibleRows, nextCursor, totalEstimate == null ? 0 : totalEstimate, hasNextPage);
    }

    private List<QueueDepthByType> queryDepthByType() {
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

    private Duration queryOldestUnleasedJobAge() {
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
        if (oldestCreatedAt == null) {
            return Duration.ZERO;
        }
        Instant now = clock.instant();
        Duration age = Duration.between(oldestCreatedAt.toInstant(), now);
        return age.isNegative() ? Duration.ZERO : age;
    }

    private List<RetryDistributionBucket> queryRetryHistogram() {
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
        // Fill missing buckets with 0 so the histogram is dense from 0..4.
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
     * 24h-bounded failure rate (R-8E-H2). Denominator is rows created in the last 24h, NOT lifetime
     * total. NULLIF guards against a divide-by-zero on a fresh table.
     */
    private double queryFailureRateLast24h() {
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

    private int queryDeadLetterCount() {
        Integer count =
                namedParameterJdbcTemplate.queryForObject(
                        "SELECT COUNT(*)::int FROM processing_job WHERE status = 'DEAD_LETTER'",
                        new MapSqlParameterSource(),
                        Integer.class);
        return count == null ? 0 : count;
    }

    private int queryAdminRequeuedLast24h() {
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

    private static int clampLimit(int limit) {
        if (limit <= 0) {
            return DEAD_LETTER_PAGE_DEFAULT_LIMIT;
        }
        return Math.min(limit, DEAD_LETTER_PAGE_MAX_LIMIT);
    }

    private static int parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(cursor));
        } catch (NumberFormatException numberFormatException) {
            return 0;
        }
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
