package com.zeromail.core.cleanup.usecases;

import com.zeromail.core.cleanup.domain.UnsubscribeAttemptState;
import com.zeromail.core.cleanup.persistence.ProcessingJobEntity;
import com.zeromail.core.cleanup.persistence.ProcessingJobRepository;
import com.zeromail.core.cleanup.persistence.UnsubscribeAttemptEntity;
import com.zeromail.core.cleanup.persistence.UnsubscribeAttemptRepository;
import com.zeromail.core.cleanup.persistence.UnsubscribeCampaignEntity;
import com.zeromail.core.cleanup.persistence.UnsubscribeCampaignRepository;
import com.zeromail.core.cleanup.projection.CampaignStatusProjection;
import com.zeromail.core.cleanup.projection.PerSenderAttemptProjection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UNS-05 — Read-side query for the campaign status polling endpoint. Builds a {@link
 * CampaignStatusProjection} containing the campaign aggregate plus a stable-ordered list of
 * per-sender attempt rows so the frontend can render the in-flight UI from the very first poll.
 *
 * <p>Polling source for D-04: because {@link CampaignExecuteService} commits campaign + N attempts
 * + processing_job in a single transaction, the projection's {@code perSender} list is non-empty
 * from {@code status='QUEUED'} forward. The frontend never has to handle a "campaign exists but has
 * no attempts yet" transient state.
 *
 * <p>Resolves jobId → campaign through {@link UnsubscribeCampaignRepository}'s {@code job_id}
 * lookup so the controller can take the user's polling key (which is the job id) and join through
 * the campaign aggregate without a separate frontend mapping.
 *
 * <p>Privacy invariant (UNS-09): the log line records the jobId only, never per-sender emails.
 */
@Service
@Transactional(readOnly = true)
public class CampaignStatusQueryService {

    private static final Logger log = LoggerFactory.getLogger(CampaignStatusQueryService.class);

    private static final String CAMPAIGN_ID_BY_JOB_SQL =
            "SELECT id FROM unsubscribe_campaign WHERE job_id = ? AND tenant_id = ?";

    private final ProcessingJobRepository processingJobRepository;
    private final UnsubscribeCampaignRepository unsubscribeCampaignRepository;
    private final UnsubscribeAttemptRepository unsubscribeAttemptRepository;
    private final JdbcTemplate jdbcTemplate;

    public CampaignStatusQueryService(
            ProcessingJobRepository processingJobRepository,
            UnsubscribeCampaignRepository unsubscribeCampaignRepository,
            UnsubscribeAttemptRepository unsubscribeAttemptRepository,
            JdbcTemplate jdbcTemplate) {
        this.processingJobRepository =
                Objects.requireNonNull(
                        processingJobRepository, "processingJobRepository must not be null");
        this.unsubscribeCampaignRepository =
                Objects.requireNonNull(
                        unsubscribeCampaignRepository,
                        "unsubscribeCampaignRepository must not be null");
        this.unsubscribeAttemptRepository =
                Objects.requireNonNull(
                        unsubscribeAttemptRepository,
                        "unsubscribeAttemptRepository must not be null");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /**
     * Return the projection for the campaign linked to {@code jobId}, scoped to the supplied
     * tenant. {@link Optional#empty()} means either the job does not exist for this tenant or no
     * campaign row references it — the controller maps both to HTTP 404 without leaking the
     * difference.
     */
    public Optional<CampaignStatusProjection> findByJobId(UUID tenantId, UUID jobId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(jobId, "jobId must not be null");

        Optional<ProcessingJobEntity> jobOptional =
                processingJobRepository.findByIdAndTenantId(jobId, tenantId);
        if (jobOptional.isEmpty()) {
            return Optional.empty();
        }

        List<UUID> campaignIds =
                jdbcTemplate.queryForList(CAMPAIGN_ID_BY_JOB_SQL, UUID.class, jobId, tenantId);
        if (campaignIds.isEmpty()) {
            return Optional.empty();
        }
        UUID campaignId = campaignIds.get(0);

        Optional<UnsubscribeCampaignEntity> campaignOptional =
                unsubscribeCampaignRepository.findByIdAndTenantId(campaignId, tenantId);
        if (campaignOptional.isEmpty()) {
            return Optional.empty();
        }
        UnsubscribeCampaignEntity campaign = campaignOptional.orElseThrow();

        List<UnsubscribeAttemptEntity> attempts =
                unsubscribeAttemptRepository.findByCampaignIdOrderBySenderEmailAsc(campaignId);
        List<PerSenderAttemptProjection> perSender =
                attempts.stream().map(CampaignStatusQueryService::toPerSenderProjection).toList();

        log.info(
                "event=cleanup_campaign_status_queried tenantId={} jobId={} campaignId={} attemptCount={}",
                tenantId,
                jobId,
                campaignId,
                attempts.size());

        return Optional.of(
                new CampaignStatusProjection(
                        campaign.getId(),
                        campaign.getJobId(),
                        campaign.getStatus(),
                        campaign.getAppliedAt(),
                        campaign.getRevertedAt(),
                        campaign.getTotalSenderCount(),
                        campaign.getTotalHistoryMessageCount(),
                        campaign.getCreatedAt(),
                        perSender));
    }

    /**
     * Compute the integer percent of terminal attempts (OK + FAILED) over the attempt total. {@code
     * 100L} forces long arithmetic so the numerator never overflows for the legal cap range
     * (totalAttempts &le; 25, terminalCount &le; 25), and the explicit empty-list guard prevents a
     * division-by-zero on a transient pre-flight campaign with zero attempts (defensive — D-04
     * commit order means this never happens in production, but the guard matches the Plan 08 Task 2
     * invariant and keeps the projection robust against future race conditions).
     */
    public static int computeProgressPct(List<UnsubscribeAttemptEntity> attempts) {
        if (attempts.isEmpty()) {
            return 0;
        }
        long terminalCount =
                attempts.stream()
                        .filter(
                                attempt ->
                                        attempt.getState() == UnsubscribeAttemptState.OK
                                                || attempt.getState()
                                                        == UnsubscribeAttemptState.FAILED)
                        .count();
        return (int) (terminalCount * 100L / attempts.size());
    }

    private static PerSenderAttemptProjection toPerSenderProjection(
            UnsubscribeAttemptEntity attempt) {
        return new PerSenderAttemptProjection(
                attempt.getSenderEmail(),
                attempt.getSenderDomain(),
                attempt.getUnsubscribeMethod(),
                attempt.getState(),
                attempt.getFailureReason(),
                attempt.getArchivedMessageCount(),
                attempt.getStartedAt(),
                attempt.getFinishedAt());
    }
}
