package com.zeromail.worker.notification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.function.Supplier;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DigestDispatchScheduler {

    static final String DISPATCH_CRON = "0 5 * * * *";
    static final String LOCK_NAME = "digestDispatchScheduler";

    /**
     * Per-cron-tick fanout cap. Each tenant dispatch is a chain of DB queries + an outbound SMTP
     * send, so the cap is governed by the SMTP provider rate budget and the JDBC pool width, not
     * Gmail quota.
     */
    private static final int DIGEST_DISPATCH_CONCURRENCY = 16;

    private static final Logger log = LoggerFactory.getLogger(DigestDispatchScheduler.class);
    private static final String DUE_TENANT_SQL =
            """
            SELECT DISTINCT ON (due.tenant_id, due.digest_day_local)
                   due.tenant_id,
                   due.time_zone,
                   due.digest_send_hour_local,
                   due.preferred_language,
                   due.digest_day_local
            FROM (
                SELECT np.tenant_id,
                       t.time_zone,
                       np.digest_send_hour_local,
                       first_user.preferred_language,
                       (?::timestamptz AT TIME ZONE t.time_zone)::date AS digest_day_local,
                       0 AS dispatch_priority
                FROM notification_preference np
                JOIN tenants t ON t.id = np.tenant_id
                JOIN LATERAL (
                    SELECT u.preferred_language
                    FROM users u
                    WHERE u.tenant_id = t.id
                    ORDER BY u.created_at ASC, u.id ASC
                    LIMIT 1
                ) first_user ON true
                WHERE np.digest_enabled = true
                  AND np.channel = 'EMAIL'
                  AND EXTRACT(HOUR FROM (?::timestamptz AT TIME ZONE t.time_zone))::int = np.digest_send_hour_local

                UNION ALL

                SELECT dd.tenant_id,
                       t.time_zone,
                       np.digest_send_hour_local,
                       first_user.preferred_language,
                       dd.digest_day_local,
                       1 AS dispatch_priority
                FROM digest_delivery dd
                JOIN notification_preference np ON np.tenant_id = dd.tenant_id
                JOIN tenants t ON t.id = dd.tenant_id
                JOIN LATERAL (
                    SELECT u.preferred_language
                    FROM users u
                    WHERE u.tenant_id = t.id
                    ORDER BY u.created_at ASC, u.id ASC
                    LIMIT 1
                ) first_user ON true
                WHERE np.digest_enabled = true
                  AND np.channel = 'EMAIL'
                  AND dd.status = 'PENDING'
                  AND dd.next_attempt_at IS NOT NULL
                  AND dd.next_attempt_at <= ?::timestamptz
            ) due
            ORDER BY due.tenant_id, due.digest_day_local, due.dispatch_priority
            """;

    private final JdbcTemplate jdbcTemplate;
    private final DigestDispatchTenantWorker digestDispatchTenantWorker;
    private final Supplier<Instant> currentInstant;

    public DigestDispatchScheduler(
            JdbcTemplate jdbcTemplate,
            DigestDispatchTenantWorker digestDispatchTenantWorker,
            Supplier<Instant> currentInstant) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.digestDispatchTenantWorker =
                Objects.requireNonNull(
                        digestDispatchTenantWorker, "digestDispatchTenantWorker must not be null");
        this.currentInstant =
                Objects.requireNonNull(currentInstant, "currentInstant must not be null");
    }

    @Scheduled(cron = DISPATCH_CRON)
    @SchedulerLock(name = LOCK_NAME, lockAtLeastFor = "PT1M", lockAtMostFor = "PT20M")
    public void scheduledDispatch() {
        LockAssert.assertLocked();
        Instant referenceInstant = currentInstant.get();
        List<DigestDueTenant> dueTenants = findTenantsDueForDigest(referenceInstant);
        if (dueTenants.isEmpty()) {
            return;
        }

        Semaphore concurrencyLimiter = new Semaphore(DIGEST_DISPATCH_CONCURRENCY);
        try (var scope = StructuredTaskScope.<Void>open()) {
            for (DigestDueTenant dueTenant : dueTenants) {
                scope.fork(
                        () ->
                                dispatchWithBackpressure(
                                        dueTenant, referenceInstant, concurrencyLimiter));
            }
            scope.join();
        } catch (InterruptedException dispatchInterrupted) {
            Thread.currentThread().interrupt();
            log.warn(
                    "event=digest_dispatch_interrupted dueTenants={}",
                    dueTenants.size(),
                    dispatchInterrupted);
        }
    }

    private Void dispatchWithBackpressure(
            DigestDueTenant dueTenant, Instant referenceInstant, Semaphore concurrencyLimiter)
            throws InterruptedException {
        concurrencyLimiter.acquire();
        try {
            digestDispatchTenantWorker.dispatchOne(dueTenant, referenceInstant);
        } catch (RuntimeException runtimeException) {
            // Failure isolation: one tenant blowing up must not cancel siblings in the
            // structured scope. Logging stays at WARN to match the original loop's posture.
            log.warn(
                    "event=digest_tenant_failed tenantId={} failureType={}",
                    dueTenant.tenantId(),
                    runtimeException.getClass().getSimpleName(),
                    runtimeException);
        } finally {
            concurrencyLimiter.release();
        }
        return null;
    }

    List<DigestDueTenant> findTenantsDueForDigest(Instant referenceInstant) {
        return jdbcTemplate.query(
                DUE_TENANT_SQL,
                (resultSet, _) ->
                        new DigestDueTenant(
                                resultSet.getObject("tenant_id", UUID.class),
                                resultSet.getString("time_zone"),
                                resultSet.getInt("digest_send_hour_local"),
                                resultSet.getString("preferred_language"),
                                resultSet.getObject("digest_day_local", LocalDate.class)),
                OffsetDateTime.ofInstant(referenceInstant, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(referenceInstant, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(referenceInstant, ZoneOffset.UTC));
    }
}
