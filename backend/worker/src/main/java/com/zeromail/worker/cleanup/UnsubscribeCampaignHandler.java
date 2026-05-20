package com.zeromail.worker.cleanup;

import com.zeromail.core.cleanup.domain.CampaignStatus;
import com.zeromail.core.cleanup.domain.UnsubscribeAttemptState;
import com.zeromail.core.cleanup.domain.UnsubscribeCampaignPolicy;
import com.zeromail.core.cleanup.domain.UnsubscribeResult;
import com.zeromail.core.cleanup.exception.ThrottleDeferredException;
import com.zeromail.core.cleanup.persistence.UnsubscribeAttemptEntity;
import com.zeromail.core.cleanup.persistence.UnsubscribeAttemptRepository;
import com.zeromail.core.cleanup.persistence.UnsubscribeCampaignEntity;
import com.zeromail.core.cleanup.persistence.UnsubscribeCampaignRepository;
import com.zeromail.core.cleanup.usecases.UnsubscribeDomainThrottle;
import com.zeromail.core.cleanup.usecases.UnsubscribeHttpClient;
import com.zeromail.core.cleanup.usecases.UnsubscribeMailtoSender;
import com.zeromail.core.triage.persistence.TriageAuditWriter;
import com.zeromail.core.triage.usecases.TriageGmailWriter;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * UNS-04 — Per-sender atomic unsubscribe handler invoked by {@link ProcessingJobWorker} for every
 * {@code processing_job} row with {@code job_type='UNSUBSCRIBE_CAMPAIGN'}.
 *
 * <p>For each pending {@link UnsubscribeAttemptEntity} on the campaign:
 *
 * <ol>
 *   <li>{@link UnsubscribeDomainThrottle#acquire} per-tenant per-domain (D-20). Denied → re-queue
 *       the job ({@code status='QUEUED'} + {@code next_run_at = NOW() + 60s}) and throw {@link
 *       ThrottleDeferredException} so the worker leaves the row QUEUED (M-2).
 *   <li>Perform the unsubscribe step: HTTP one-click via {@link UnsubscribeHttpClient} (RFC 8058)
 *       or Gmail send-as-self mailto via {@link UnsubscribeMailtoSender} (RFC 6068). Both use a
 *       URL/mailto value <i>read from the persisted {@code mail_message_observed} row</i> — D-11
 *       provenance check, no user-supplied input is ever sent.
 *   <li>On success, capture the Gmail label id once via {@link TriageGmailWriter#ensureLabelExists}
 *       and apply-label + archive + audit-write every history message from that sender (look-up via
 *       {@code mail_message_observed}). On failure, mark the attempt FAILED and skip label/archive
 *       — per-sender atomic: a sender's history is touched <i>only</i> when the unsubscribe step
 *       itself succeeded.
 * </ol>
 *
 * <p>Privacy: log lines record {@code tenantId} + {@code senderDomain} + counts only. The full
 * {@code senderEmail}, raw URL, raw mailto, and message-id values are never logged.
 */
@Component
@SuppressWarnings("SqlResolve")
public class UnsubscribeCampaignHandler {

    private static final Logger log = LoggerFactory.getLogger(UnsubscribeCampaignHandler.class);
    private static final String UNKNOWN_DOMAIN = "unknown";
    private static final String LOOKUP_URL_SQL =
            "SELECT list_unsubscribe_url FROM mail_message_observed "
                    + "WHERE tenant_id = ? AND sender_email = ? AND list_unsubscribe_url IS NOT NULL "
                    + "LIMIT 1";
    private static final String LOOKUP_MAILTO_SQL =
            "SELECT list_unsubscribe_mailto FROM mail_message_observed "
                    + "WHERE tenant_id = ? AND sender_email = ? AND list_unsubscribe_mailto IS NOT NULL "
                    + "LIMIT 1";
    private static final String LOOKUP_HISTORY_MESSAGE_IDS_SQL =
            "SELECT gmail_message_id FROM mail_message_observed "
                    + "WHERE tenant_id = ? AND sender_email = ?";
    private static final String REQUEUE_JOB_SQL =
            "UPDATE processing_job "
                    + "SET status = 'QUEUED', "
                    + "    next_run_at = NOW() + INTERVAL '60 seconds', "
                    + "    heartbeat_at = NULL, "
                    + "    updated_at = NOW() "
                    + "WHERE id = ?";
    private static final String HEARTBEAT_SQL =
            "UPDATE processing_job SET heartbeat_at = NOW(), updated_at = NOW() WHERE id = ?";

    private final UnsubscribeCampaignRepository unsubscribeCampaignRepository;
    private final UnsubscribeAttemptRepository unsubscribeAttemptRepository;
    private final UnsubscribeHttpClient unsubscribeHttpClient;
    private final UnsubscribeMailtoSender unsubscribeMailtoSender;
    private final UnsubscribeDomainThrottle unsubscribeDomainThrottle;
    private final TriageGmailWriter triageGmailWriter;
    private final TriageAuditWriter triageAuditWriter;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public UnsubscribeCampaignHandler(
            UnsubscribeCampaignRepository unsubscribeCampaignRepository,
            UnsubscribeAttemptRepository unsubscribeAttemptRepository,
            UnsubscribeHttpClient unsubscribeHttpClient,
            UnsubscribeMailtoSender unsubscribeMailtoSender,
            UnsubscribeDomainThrottle unsubscribeDomainThrottle,
            TriageGmailWriter triageGmailWriter,
            TriageAuditWriter triageAuditWriter,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            Clock clock) {
        this.unsubscribeCampaignRepository =
                Objects.requireNonNull(
                        unsubscribeCampaignRepository,
                        "unsubscribeCampaignRepository must not be null");
        this.unsubscribeAttemptRepository =
                Objects.requireNonNull(
                        unsubscribeAttemptRepository,
                        "unsubscribeAttemptRepository must not be null");
        this.unsubscribeHttpClient =
                Objects.requireNonNull(
                        unsubscribeHttpClient, "unsubscribeHttpClient must not be null");
        this.unsubscribeMailtoSender =
                Objects.requireNonNull(
                        unsubscribeMailtoSender, "unsubscribeMailtoSender must not be null");
        this.unsubscribeDomainThrottle =
                Objects.requireNonNull(
                        unsubscribeDomainThrottle, "unsubscribeDomainThrottle must not be null");
        this.triageGmailWriter =
                Objects.requireNonNull(triageGmailWriter, "triageGmailWriter must not be null");
        this.triageAuditWriter =
                Objects.requireNonNull(triageAuditWriter, "triageAuditWriter must not be null");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Entry point invoked by {@link ProcessingJobWorker#dispatchJob}. {@code TenantContext.TENANT}
     * is already bound to {@code tenantId} when this method runs.
     */
    public void handle(UUID jobId, UUID tenantId, String payloadJson) {
        UnsubscribeCampaignPayload payload = parsePayload(payloadJson);
        UnsubscribeCampaignEntity campaign =
                unsubscribeCampaignRepository
                        .findByIdAndTenantId(payload.campaignId(), tenantId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Campaign not found: " + payload.campaignId()));
        if (campaign.getStatus() == CampaignStatus.QUEUED) {
            campaign.markRunning();
            unsubscribeCampaignRepository.save(campaign);
        }

        List<UnsubscribeAttemptEntity> attempts =
                unsubscribeAttemptRepository.findByCampaignIdOrderBySenderEmailAsc(
                        campaign.getCampaignId());
        for (UnsubscribeAttemptEntity attempt : attempts) {
            switch (attempt.getState()) {
                case OK, FAILED -> {
                    // Terminal in this campaign; do not re-run on retry pickup.
                }
                case PENDING, RUNNING -> {
                    if (attempt.getState() != UnsubscribeAttemptState.PENDING) {
                        // Re-queued after a previous throttle deferral mid-flight. Reset cleanly.
                        attempt.resetToPending();
                        unsubscribeAttemptRepository.save(attempt);
                    }
                    executeSingleAttempt(jobId, tenantId, attempt);
                    updateHeartbeat(jobId);
                }
            }
        }

        if (campaign.getStatus() == CampaignStatus.RUNNING) {
            campaign.markCompleted(clock.instant());
            unsubscribeCampaignRepository.save(campaign);
        }
        log.info(
                "event=cleanup_campaign_completed tenantId={} campaignId={} jobId={}",
                tenantId,
                campaign.getCampaignId(),
                jobId);
    }

    /**
     * Execute a single sender's unsubscribe step + label/archive history. Throws {@link
     * ThrottleDeferredException} when the throttle bucket refuses the attempt (after re-queueing
     * the job row + resetting the attempt to PENDING so the next pickup can retry it).
     */
    private void executeSingleAttempt(UUID jobId, UUID tenantId, UnsubscribeAttemptEntity attempt) {
        // Throttle check FIRST — before flipping state. If denied, the attempt remains PENDING
        // and the job is re-queued for the next poll cycle.
        if (!unsubscribeDomainThrottle.acquire(tenantId, attempt.getSenderDomain())) {
            jdbcTemplate.update(REQUEUE_JOB_SQL, jobId);
            log.info(
                    "event=cleanup_attempt_deferred tenantId={} senderDomain={}",
                    tenantId,
                    attempt.getSenderDomain());
            throw new ThrottleDeferredException(
                    tenantId, attempt.getSenderDomain(), "PER_DOMAIN_60S_EXCEEDED");
        }

        attempt.markRunning(clock.instant());
        unsubscribeAttemptRepository.save(attempt);

        UnsubscribeResult result = invokeUnsubscribeTransport(tenantId, attempt);

        if (result instanceof UnsubscribeResult.Failed failed) {
            attempt.markFailed(failed.failureReason(), clock.instant());
            unsubscribeAttemptRepository.save(attempt);
            log.info(
                    "event=cleanup_campaign_step_failed tenantId={} senderDomain={} failureReason={}",
                    tenantId,
                    attempt.getSenderDomain(),
                    failed.failureReason());
            return;
        }

        // Per-sender atomic: label + archive ONLY when unsubscribe step succeeded.
        int archivedCount = applyLabelAndArchiveHistory(tenantId, attempt);
        attempt.markOk(archivedCount, clock.instant());
        unsubscribeAttemptRepository.save(attempt);
        log.info(
                "event=cleanup_campaign_step_ok tenantId={} senderDomain={} archivedCount={}",
                tenantId,
                attempt.getSenderDomain(),
                archivedCount);
    }

    private UnsubscribeResult invokeUnsubscribeTransport(
            UUID tenantId, UnsubscribeAttemptEntity attempt) {
        try {
            return switch (attempt.getUnsubscribeMethod()) {
                case ONE_CLICK -> {
                    String persistedUrl =
                            lookupPersistedHttpsUrl(tenantId, attempt.getSenderEmail());
                    yield unsubscribeHttpClient.postOneClick(tenantId, persistedUrl);
                }
                case MAILTO -> {
                    String persistedMailto =
                            lookupPersistedMailtoValue(tenantId, attempt.getSenderEmail());
                    yield unsubscribeMailtoSender.sendUnsubscribeMailto(
                            tenantId, null, persistedMailto, persistedMailto);
                }
                case NONE -> UnsubscribeResult.failed("NO_HEADER");
            };
        } catch (IllegalArgumentException invalidPersistedHandle) {
            log.warn(
                    "event=cleanup_campaign_step_failed tenantId={} senderDomain={} failureReason=INVALID_PERSISTED_HANDLE",
                    tenantId,
                    attempt.getSenderDomain());
            return UnsubscribeResult.failed("INVALID_PERSISTED_HANDLE");
        }
    }

    /**
     * Look up the persisted {@code list_unsubscribe_url} for the sender — D-11 provenance check.
     * Worker NEVER takes a URL from anywhere else (no controller input, no manual override).
     */
    private String lookupPersistedHttpsUrl(UUID tenantId, String senderEmail) {
        List<String> urls =
                jdbcTemplate.queryForList(LOOKUP_URL_SQL, String.class, tenantId, senderEmail);
        if (urls.isEmpty()) {
            throw new IllegalArgumentException(
                    "No persisted list_unsubscribe_url found for sender (domain="
                            + extractDomain(senderEmail)
                            + ")");
        }
        return urls.get(0);
    }

    private String lookupPersistedMailtoValue(UUID tenantId, String senderEmail) {
        List<String> mailtos =
                jdbcTemplate.queryForList(LOOKUP_MAILTO_SQL, String.class, tenantId, senderEmail);
        if (mailtos.isEmpty()) {
            throw new IllegalArgumentException(
                    "No persisted list_unsubscribe_mailto found for sender (domain="
                            + extractDomain(senderEmail)
                            + ")");
        }
        return mailtos.get(0);
    }

    private int applyLabelAndArchiveHistory(UUID tenantId, UnsubscribeAttemptEntity attempt) {
        String labelId;
        try {
            labelId =
                    triageGmailWriter.ensureLabelExists(
                            tenantId, UnsubscribeCampaignPolicy.UNSUBSCRIBED_LABEL_NAME);
        } catch (IOException labelFailure) {
            throw new IllegalStateException(
                    "Unable to ensure Gmail label '"
                            + UnsubscribeCampaignPolicy.UNSUBSCRIBED_LABEL_NAME
                            + "'",
                    labelFailure);
        }

        List<String> historyMessageIds =
                jdbcTemplate.queryForList(
                        LOOKUP_HISTORY_MESSAGE_IDS_SQL,
                        String.class,
                        tenantId,
                        attempt.getSenderEmail());

        int archivedCount = 0;
        for (String gmailMessageId : historyMessageIds) {
            try {
                triageGmailWriter.applyLabel(tenantId, gmailMessageId, labelId);
                triageGmailWriter.archiveSkipInbox(tenantId, gmailMessageId);
            } catch (IOException gmailFailure) {
                // Continue with the rest of the history — partial archive is the documented
                // outcome when Gmail rate-limits us mid-batch. Audit only records successful
                // archive rows so undo replays only what actually changed.
                log.warn(
                        "event=cleanup_history_archive_failed tenantId={} senderDomain={}",
                        tenantId,
                        attempt.getSenderDomain());
                continue;
            }
            triageAuditWriter.recordCleanupArchive(
                    tenantId,
                    gmailMessageId,
                    attempt.getAttemptId(),
                    labelId,
                    attempt.getSenderEmail());
            archivedCount++;
        }
        return archivedCount;
    }

    private void updateHeartbeat(UUID jobId) {
        jdbcTemplate.update(HEARTBEAT_SQL, jobId);
    }

    private UnsubscribeCampaignPayload parsePayload(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, UnsubscribeCampaignPayload.class);
        } catch (JacksonException malformedPayload) {
            throw new IllegalStateException("Malformed campaign payload", malformedPayload);
        }
    }

    private static String extractDomain(String senderEmail) {
        if (senderEmail == null) {
            return UNKNOWN_DOMAIN;
        }
        int atIndex = senderEmail.lastIndexOf('@');
        if (atIndex < 0 || atIndex == senderEmail.length() - 1) {
            return UNKNOWN_DOMAIN;
        }
        return senderEmail.substring(atIndex + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * D-19 payload schema: {@code {"campaignId":"<uuid>","schemaVersion":1}}. {@code schemaVersion}
     * lets the planner extend the payload without a breaking migration when SEED-009 reuses this
     * outbox table.
     */
    private record UnsubscribeCampaignPayload(UUID campaignId, int schemaVersion) {}
}
