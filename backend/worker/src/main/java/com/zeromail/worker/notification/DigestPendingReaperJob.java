package com.zeromail.worker.notification;

import com.zeromail.core.notification.usecases.DigestDeliveryService;
import com.zeromail.core.tenant.TenantContext;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DigestPendingReaperJob {

    static final String LOCK_NAME = "digestPendingReaper";

    private static final Logger log = LoggerFactory.getLogger(DigestPendingReaperJob.class);
    private static final int BATCH_LIMIT = 500;
    private static final Duration STUCK_PENDING_GRACE_PERIOD = Duration.ofMinutes(30);
    private static final String STUCK_PENDING_SQL =
            """
            SELECT id, tenant_id
            FROM digest_delivery
            WHERE status = 'PENDING'
              AND created_at < ?
            ORDER BY created_at
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final DigestDeliveryService digestDeliveryService;

    public DigestPendingReaperJob(
            JdbcTemplate jdbcTemplate, DigestDeliveryService digestDeliveryService) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.digestDeliveryService =
                Objects.requireNonNull(
                        digestDeliveryService, "digestDeliveryService must not be null");
    }

    @Scheduled(fixedDelay = 300_000L)
    @SchedulerLock(name = LOCK_NAME, lockAtLeastFor = "PT1M", lockAtMostFor = "PT5M")
    public void scheduledReap() {
        LockAssert.assertLocked();
        reap();
    }

    public int reap() {
        int totalProcessed = 0;
        int selectedCount;
        do {
            List<StuckDigestDelivery> stuckDeliveries = findStuckPending();
            selectedCount = stuckDeliveries.size();
            for (StuckDigestDelivery stuckDelivery : stuckDeliveries) {
                ScopedValue.where(TenantContext.TENANT, stuckDelivery.tenantId().toString())
                        .run(
                                () ->
                                        digestDeliveryService.markFailed(
                                                stuckDelivery.deliveryId(),
                                                "reaper_stuck_pending"));
                totalProcessed++;
            }
        } while (selectedCount == BATCH_LIMIT);

        log.info("event=digest_pending_reaped tenantId=system totalProcessed={}", totalProcessed);
        return totalProcessed;
    }

    private List<StuckDigestDelivery> findStuckPending() {
        Instant cutoff = Instant.now().minus(STUCK_PENDING_GRACE_PERIOD);
        return jdbcTemplate.query(
                STUCK_PENDING_SQL,
                (resultSet, _) ->
                        new StuckDigestDelivery(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getObject("tenant_id", UUID.class)),
                Timestamp.from(cutoff),
                BATCH_LIMIT);
    }

    private record StuckDigestDelivery(UUID deliveryId, UUID tenantId) {}
}
