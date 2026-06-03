package com.zeromail.core.admin.queue.usecases;

import com.zeromail.core.admin.queue.persistence.lowlevel.QueueHealthReadRepository;
import com.zeromail.core.admin.queue.projection.FailureWindow24h;
import com.zeromail.core.admin.queue.projection.JobDetail;
import com.zeromail.core.admin.queue.projection.JobPage;
import com.zeromail.core.admin.queue.projection.JobRow;
import com.zeromail.core.admin.queue.projection.QueueDepthByType;
import com.zeromail.core.admin.queue.projection.QueueHealthSnapshot;
import com.zeromail.core.admin.queue.projection.RetryDistributionBucket;
import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates queue-health read queries. Raw SQL lives in {@link QueueHealthReadRepository}; this
 * service composes the results into the {@link QueueHealthSnapshot} projection and handles
 * pagination cursor logic.
 *
 * <p>{@code failureRateLast24h} uses a 24h-bounded denominator (R-8E-H2). TODO v1.3+: per-job-type
 * failure histogram (R-8E-H5 deferred).
 */
@Service
public class QueueHealthQueryService {

    private static final int JOB_PAGE_DEFAULT_LIMIT = 25;
    private static final int JOB_PAGE_MAX_LIMIT = 100;

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
        FailureWindow24h failureWindow = queueHealthReadRepository.findFailureWindowLast24h();
        int deadLetterCount = queueHealthReadRepository.findDeadLetterCount();
        return new QueueHealthSnapshot(
                depthByType,
                oldestUnleasedJobAge,
                retryHistogram,
                failureWindow.rate(),
                failureWindow.failedCount(),
                failureWindow.sampleSize(),
                deadLetterCount,
                clock.instant());
    }

    /**
     * Unified, paginated job list across every {@code processing_job} job type. {@code status} and
     * {@code jobType} are optional filters; {@code status='SCHEDULED'} selects future-dated PENDING
     * rows. Uses an offset-encoded cursor.
     */
    @Transactional(readOnly = true)
    public JobPage jobsPage(String status, String jobType, String cursor, int limit) {
        int safeLimit = clampLimit(limit);
        int offset = parseCursor(cursor);
        List<JobRow> rows =
                queueHealthReadRepository.findJobsPage(status, jobType, offset, safeLimit + 1);
        boolean hasNextPage = rows.size() > safeLimit;
        List<JobRow> visibleRows = hasNextPage ? rows.subList(0, safeLimit) : rows;
        int totalEstimate = queueHealthReadRepository.findJobsCount(status, jobType);
        String nextCursor = hasNextPage ? String.valueOf(offset + safeLimit) : null;
        return new JobPage(visibleRows, nextCursor, totalEstimate, hasNextPage);
    }

    @Transactional(readOnly = true)
    public JobDetail jobDetail(UUID jobId) {
        return queueHealthReadRepository
                .findJobDetail(jobId)
                .orElseThrow(() -> new QueueJobNotFoundException(jobId));
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
            return JOB_PAGE_DEFAULT_LIMIT;
        }
        return Math.min(limit, JOB_PAGE_MAX_LIMIT);
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

    /**
     * Raised when a job detail / action targets a {@code processing_job} id that does not exist.
     */
    public static class QueueJobNotFoundException extends AdminBusinessException {

        public QueueJobNotFoundException(UUID jobId) {
            super("Processing job not found: " + jobId);
        }

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.NOT_FOUND;
        }

        @Override
        public String errorCode() {
            return "error.admin.queue_job_not_found";
        }

        @Override
        public String logEvent() {
            return "admin_queue_job_not_found";
        }

        @Override
        public String detail() {
            return "The requested job could not be located.";
        }
    }
}
