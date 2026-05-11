package com.zeromail.worker.triage;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

@Component
public class TriageEventRetryJob {

  private static final Logger log = LoggerFactory.getLogger(TriageEventRetryJob.class);
  private static final Duration OUTSTANDING_PUBLICATION_MINIMUM_AGE = Duration.ofMinutes(5);

  private final IncompleteEventPublications incompleteEventPublications;
  private final FailedEventPublications failedEventPublications;
  private final Counter retryRunCounter;

  public TriageEventRetryJob(
      IncompleteEventPublications incompleteEventPublications,
      FailedEventPublications failedEventPublications,
      MeterRegistry meterRegistry) {
    this.incompleteEventPublications = incompleteEventPublications;
    this.failedEventPublications = failedEventPublications;
    retryRunCounter =
        Counter.builder("zero_mail.triage.event_retry.run_total")
            .description("Triage event publication retry job runs")
            .register(meterRegistry);
  }

  @Scheduled(fixedDelay = 120_000L)
  @SchedulerLock(name = "triageEventRetry", lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")
  public void resubmitOutstandingPublications() {
    incompleteEventPublications.resubmitIncompletePublicationsOlderThan(
        OUTSTANDING_PUBLICATION_MINIMUM_AGE);
    failedEventPublications.resubmit(
        ResubmissionOptions.defaults().withMinAge(OUTSTANDING_PUBLICATION_MINIMUM_AGE));
    retryRunCounter.increment();
    log.info(
        "event=triage_event_retry_run tenantId=system minimumAgeSeconds={}",
        OUTSTANDING_PUBLICATION_MINIMUM_AGE.toSeconds());
  }
}
