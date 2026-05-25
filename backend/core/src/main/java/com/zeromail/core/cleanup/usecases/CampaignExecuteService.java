package com.zeromail.core.cleanup.usecases;

import com.zeromail.core.cleanup.persistence.ProcessingJobEntity;
import com.zeromail.core.cleanup.persistence.ProcessingJobRepository;
import com.zeromail.core.cleanup.persistence.UnsubscribeAttemptEntity;
import com.zeromail.core.cleanup.persistence.UnsubscribeAttemptRepository;
import com.zeromail.core.cleanup.persistence.UnsubscribeCampaignEntity;
import com.zeromail.core.cleanup.persistence.UnsubscribeCampaignRepository;
import com.zeromail.core.cleanup.usecases.CampaignPreviewService.CampaignPreviewResult;
import com.zeromail.core.cleanup.usecases.CampaignPreviewService.PerSenderPreview;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * UNS-04 — Transactional INSERT path that turns a confirmed campaign selection into one {@code
 * unsubscribe_campaign} row plus N {@code unsubscribe_attempt} rows plus one {@code processing_job}
 * row, all in a single {@code REQUIRED} transaction (D-04).
 *
 * <p>The {@code processing_job} row is inserted LAST so the worker's {@code SKIP LOCKED} pickup
 * (D-02) can never observe the job before its children exist — there is no race where the worker
 * claims a job whose campaign row is still uncommitted. Once the job id is known, {@link
 * UnsubscribeCampaignEntity#linkJob(UUID)} backfills the FK so the read-side ({@link
 * CampaignStatusQueryService}) can resolve campaign → job either direction.
 *
 * <p>Defense-in-depth: this service re-runs {@link CampaignPreviewService#preview} even when the
 * controller already did, so a buggy/missing controller validation cannot bypass the policy caps.
 * Only senders whose {@link PerSenderPreview#willArchive()} returns true become attempts —
 * suppressed / {@code NONE}-method senders are silently dropped (preview already surfaced the risk
 * badge to the user).
 *
 * <p>Payload schema (D-19): {@code {"campaignId":"<uuid>","schemaVersion":1}}. {@code
 * schemaVersion} lets future payload additions roll out without a breaking migration when SEED-009
 * reuses the same outbox table.
 *
 * <p>Privacy invariant (UNS-09): the log line records counts only — the sender list is never
 * iterated into log statements.
 */
@Service
@Transactional
public class CampaignExecuteService {

    private static final Logger log = LoggerFactory.getLogger(CampaignExecuteService.class);
    private static final String JOB_TYPE_UNSUBSCRIBE_CAMPAIGN = "UNSUBSCRIBE_CAMPAIGN";
    private static final int CAMPAIGN_PAYLOAD_SCHEMA_VERSION = 1;

    private final CampaignPreviewService campaignPreviewService;
    private final UnsubscribeCampaignRepository unsubscribeCampaignRepository;
    private final UnsubscribeAttemptRepository unsubscribeAttemptRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final ObjectMapper objectMapper;

    public CampaignExecuteService(
            CampaignPreviewService campaignPreviewService,
            UnsubscribeCampaignRepository unsubscribeCampaignRepository,
            UnsubscribeAttemptRepository unsubscribeAttemptRepository,
            ProcessingJobRepository processingJobRepository,
            ObjectMapper objectMapper) {
        this.campaignPreviewService =
                Objects.requireNonNull(
                        campaignPreviewService, "campaignPreviewService must not be null");
        this.unsubscribeCampaignRepository =
                Objects.requireNonNull(
                        unsubscribeCampaignRepository,
                        "unsubscribeCampaignRepository must not be null");
        this.unsubscribeAttemptRepository =
                Objects.requireNonNull(
                        unsubscribeAttemptRepository,
                        "unsubscribeAttemptRepository must not be null");
        this.processingJobRepository =
                Objects.requireNonNull(
                        processingJobRepository, "processingJobRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public CampaignExecuteResult execute(UUID tenantId, List<String> senderEmails) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(senderEmails, "senderEmails must not be null");

        CampaignPreviewResult preview = campaignPreviewService.preview(tenantId, senderEmails);
        List<PerSenderPreview> archivable =
                preview.perSender().stream().filter(PerSenderPreview::willArchive).toList();
        if (archivable.isEmpty()) {
            throw new IllegalStateException(
                    "No archivable senders in selection — every sender is either suppressed or has"
                            + " no usable List-Unsubscribe header.");
        }

        UUID campaignId = UUID.randomUUID();
        UnsubscribeCampaignEntity campaign =
                new UnsubscribeCampaignEntity(
                        campaignId, tenantId, archivable.size(), preview.archivableHistoryCount());
        campaign = unsubscribeCampaignRepository.save(campaign);

        for (PerSenderPreview row : archivable) {
            UnsubscribeAttemptEntity attempt =
                    new UnsubscribeAttemptEntity(
                            UUID.randomUUID(),
                            campaign.getId(),
                            row.senderEmail(),
                            row.senderDomain(),
                            row.unsubscribeMethod());
            unsubscribeAttemptRepository.save(attempt);
        }

        String payloadJson = serializePayload(campaign.getId());
        UUID jobId = UUID.randomUUID();
        ProcessingJobEntity job =
                new ProcessingJobEntity(
                        jobId, tenantId, JOB_TYPE_UNSUBSCRIBE_CAMPAIGN, payloadJson);
        job = processingJobRepository.save(job);

        campaign.linkJob(job.getId());
        campaign = unsubscribeCampaignRepository.save(campaign);

        log.info(
                "event=cleanup_campaign_executed tenantId={} campaignId={} jobId={} senderCount={} historyCount={}",
                tenantId,
                campaign.getId(),
                job.getId(),
                archivable.size(),
                preview.archivableHistoryCount());

        return new CampaignExecuteResult(campaign.getId(), job.getId());
    }

    private String serializePayload(UUID campaignId) {
        try {
            return objectMapper.writeValueAsString(
                    new CampaignJobPayload(campaignId, CAMPAIGN_PAYLOAD_SCHEMA_VERSION));
        } catch (JacksonException payloadSerializationFailure) {
            throw new IllegalStateException(
                    "Failed to serialize campaign job payload", payloadSerializationFailure);
        }
    }

    /** Returned to the controller; {@code jobId} is what the frontend polls for status. */
    public record CampaignExecuteResult(UUID campaignId, UUID jobId) {

        public CampaignExecuteResult {
            Objects.requireNonNull(campaignId, "campaignId must not be null");
            Objects.requireNonNull(jobId, "jobId must not be null");
        }
    }

    /** D-19 payload schema persisted into {@code processing_job.payload}. */
    private record CampaignJobPayload(UUID campaignId, int schemaVersion) {}
}
