package com.zeromail.core.admin.queue.usecases;

import com.zeromail.core.admin.queue.persistence.lowlevel.QueueHealthReadRepository;
import com.zeromail.core.admin.queue.projection.DeadLetterPage;
import com.zeromail.core.admin.queue.projection.DeadLetterRow;
import com.zeromail.core.admin.queue.projection.QueueDepthByType;
import com.zeromail.core.admin.queue.projection.QueueHealthSnapshot;
import com.zeromail.core.admin.queue.projection.RetryDistributionBucket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates queue-health read queries. Raw SQL lives in {@link QueueHealthReadRepository}; this
 * service composes the results into the {@link QueueHealthSnapshot}/{@link DeadLetterPage}
 * projections and handles pagination cursor logic.
 *
 * <p>{@code failureRateLast24h} uses a 24h-bounded denominator (R-8E-H2). TODO v1.3+: per-job-type
 * failure histogram (R-8E-H5 deferred).
 */
@Service
public class QueueHealthQueryService {

    private static final int DEAD_LETTER_PAGE_DEFAULT_LIMIT = 25;
    private static final int DEAD_LETTER_PAGE_MAX_LIMIT = 100;

    private final QueueHealthReadRepository queueHealthReadRepository;
    private final Clock clock;

    public QueueHealthQueryService(
            QueueHealthReadRepository queueHealthReadRepository, Clock clock) {
        this.queueHealthReadRepository =
                Objects.requireNonNull(
                        queueHealthReadRepository, "queueHealthReadRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(readOnly = true)
    public QueueHealthSnapshot snapshot() {
        List<QueueDepthByType> depthByType = queueHealthReadRepository.findDepthByType();
        Duration oldestUnleasedJobAge = computeOldestUnleasedJobAge();
        List<RetryDistributionBucket> retryHistogram =
                queueHealthReadRepository.findRetryHistogram();
        double failureRateLast24h = queueHealthReadRepository.findFailureRateLast24h();
        int deadLetterCount = queueHealthReadRepository.findDeadLetterCount();
        int adminRequeuedLast24h = queueHealthReadRepository.findAdminRequeuedLast24h();
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
        List<DeadLetterRow> rows =
                queueHealthReadRepository.findDeadLetterPage(offset, safeLimit + 1);
        boolean hasNextPage = rows.size() > safeLimit;
        List<DeadLetterRow> visibleRows = hasNextPage ? rows.subList(0, safeLimit) : rows;
        int totalEstimate = queueHealthReadRepository.findDeadLetterCount();
        String nextCursor = hasNextPage ? String.valueOf(offset + safeLimit) : null;
        return new DeadLetterPage(visibleRows, nextCursor, totalEstimate, hasNextPage);
    }

    private Duration computeOldestUnleasedJobAge() {
        Instant oldestCreatedAt = queueHealthReadRepository.findOldestUnleasedJobCreatedAt();
        if (oldestCreatedAt == null) {
            return Duration.ZERO;
        }
        Duration age = Duration.between(oldestCreatedAt, clock.instant());
        return age.isNegative() ? Duration.ZERO : age;
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
}
