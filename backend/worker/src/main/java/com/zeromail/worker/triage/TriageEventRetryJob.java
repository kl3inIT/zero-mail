package com.zeromail.worker.triage;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Resubmits incomplete triage event publications with exponential backoff and a hard attempt cap.
 *
 * <p>Spring Modulith 2.0.6's {@code ResubmissionOptions} has no per-event max-attempts and the
 * built-in {@code resubmitIncompletePublicationsOlderThan} resubmits EVERY incomplete event on
 * EVERY tick — which during the 2026-06-01 triage outage hammered each stuck event ~226 times with
 * no spacing. We instead drive resubmission through the {@code Predicate} overload, reading the
 * {@code completion_attempts}/{@code last_resubmission_date} that Modulith tracks per publication:
 *
 * <ul>
 *   <li><b>Exponential backoff</b>: an event is only resubmitted once {@code now -
 *       lastResubmissionDate} exceeds {@code BASE_BACKOFF * 2^attempts} (capped at MAX_BACKOFF), so
 *       a persistently-failing event is retried less and less often instead of every 120s.
 *   <li><b>Max-attempts cap</b>: past {@code MAX_ATTEMPTS} an event is dead-lettered — never
 *       resubmitted again (stops the hammering) and surfaced via the {@code event_deadlettered}
 *       gauge for alerting, so a human/recovery job handles it instead of it failing silently.
 * </ul>
 *
 * Concurrency of the resulting triage executions stays bounded by the listener-side limiter in
 * {@link com.zeromail.core.triage.usecases.TriageOrchestratorService}.
 */
@Component
public class TriageEventRetryJob {

    private static final Logger log = LoggerFactory.getLogger(TriageEventRetryJob.class);

    /** Events only become eligible once they have been outstanding at least this long. */
    private static final Duration OUTSTANDING_PUBLICATION_MINIMUM_AGE = Duration.ofMinutes(5);

    /** Hard cap: beyond this many failed attempts an event is dead-lettered, not resubmitted. */
    private static final int MAX_ATTEMPTS = 15;

    /** Backoff base; the per-event wait is BASE * 2^attempts, capped at MAX_BACKOFF. */
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(30);

    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    private final IncompleteEventPublications incompleteEventPublications;
    private final Clock clock;
    private final Counter retryRunCounter;
    private final AtomicInteger deadLetteredGaugeValue = new AtomicInteger(0);

    public TriageEventRetryJob(
            IncompleteEventPublications incompleteEventPublications,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.incompleteEventPublications = incompleteEventPublications;
        this.clock = clock;
        this.retryRunCounter =
                Counter.builder("zero_mail.triage.event_retry.run_total")
                        .description("Triage event publication retry job runs")
                        .register(meterRegistry);
        Gauge.builder(
                        "zero_mail.triage.event_deadlettered",
                        deadLetteredGaugeValue,
                        AtomicInteger::doubleValue)
                .description(
                        "Incomplete triage events past the retry cap (dead-lettered, need attention)")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelay = 120_000L)
    @SchedulerLock(name = "triageEventRetry", lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")
    public void resubmitOutstandingPublications() {
        AtomicInteger deadLetteredThisRun = new AtomicInteger(0);
        Instant now = clock.instant();

        incompleteEventPublications.resubmitIncompletePublications(
                publication -> {
                    Instant publicationDate = publication.getPublicationDate();
                    if (Duration.between(publicationDate, now)
                                    .compareTo(OUTSTANDING_PUBLICATION_MINIMUM_AGE)
                            < 0) {
                        return false;
                    }
                    int attempts = publication.getCompletionAttempts();
                    if (attempts >= MAX_ATTEMPTS) {
                        // Dead-letter: stop resubmitting. Surfaced via gauge for alerting.
                        deadLetteredThisRun.incrementAndGet();
                        return false;
                    }
                    Instant lastResubmission = publication.getLastResubmissionDate();
                    if (lastResubmission == null) {
                        return true;
                    }
                    Duration backoff = backoffFor(attempts);
                    return Duration.between(lastResubmission, now).compareTo(backoff) >= 0;
                });

        deadLetteredGaugeValue.set(deadLetteredThisRun.get());
        retryRunCounter.increment();
        log.info(
                "event=triage_event_retry_run tenantId=system minimumAgeSeconds={} deadLetteredCount={}",
                OUTSTANDING_PUBLICATION_MINIMUM_AGE.toSeconds(),
                deadLetteredThisRun.get());
    }

    private static Duration backoffFor(int attempts) {
        // BASE * 2^attempts, guarding against overflow and capping at MAX_BACKOFF.
        double seconds = BASE_BACKOFF.toSeconds() * Math.pow(2, attempts);
        long cappedSeconds = (long) Math.min(seconds, (double) MAX_BACKOFF.toSeconds());
        return Duration.ofSeconds(cappedSeconds);
    }
}
