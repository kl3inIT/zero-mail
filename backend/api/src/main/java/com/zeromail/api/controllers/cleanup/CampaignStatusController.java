package com.zeromail.api.controllers.cleanup;

import com.zeromail.api.dto.cleanup.CampaignStatusResponse;
import com.zeromail.core.cleanup.domain.CampaignStatus;
import com.zeromail.core.cleanup.domain.UnsubscribeAttemptState;
import com.zeromail.core.cleanup.domain.UnsubscribeCampaignPolicy;
import com.zeromail.core.cleanup.exception.CampaignNotFoundException;
import com.zeromail.core.cleanup.projection.CampaignStatusProjection;
import com.zeromail.core.cleanup.projection.PerSenderAttemptProjection;
import com.zeromail.core.cleanup.usecases.CampaignStatusQueryService;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UNS-05 — polling endpoint for campaign status. Thin controller per CONVENTIONS §1: extract tenant
 * context, look up the projection by {@code jobId}, compute the two derived UI fields ({@code
 * progressPct}, {@code undoAvailable}), and map to wire DTO.
 *
 * <p>{@code progressPct} is computed in the controller (not the service) because it is a
 * presentation concern — the projection carries the raw per-sender attempt list which the
 * controller turns into a {@code (terminal/total) * 100} integer percent. {@code undoAvailable} is
 * likewise presentation logic: the frontend wants a single boolean to drive the "Undo" button
 * visibility, computed against {@link UnsubscribeCampaignPolicy#undoableUntil} and the injected
 * {@link Clock} so tests can fast-forward time deterministically.
 *
 * <p>Privacy invariant (UNS-09): the log line records the jobId only, never per-sender emails.
 */
@RestController
@Tag(name = "cleanup")
@RequestMapping("/api/unsubscribe/campaigns")
public class CampaignStatusController {

    private static final Logger log = LoggerFactory.getLogger(CampaignStatusController.class);

    private final CampaignStatusQueryService campaignStatusQueryService;
    private final Clock clock;

    public CampaignStatusController(
            CampaignStatusQueryService campaignStatusQueryService, Clock clock) {
        this.campaignStatusQueryService = campaignStatusQueryService;
        this.clock = clock;
    }

    @GetMapping("/{jobId}")
    public CampaignStatusResponse getStatus(@PathVariable UUID jobId) {
        UUID tenantId = TenantContext.currentTenantUuid();
        CampaignStatusProjection projection =
                campaignStatusQueryService
                        .findByJobId(tenantId, jobId)
                        .orElseThrow(() -> new CampaignNotFoundException(jobId));

        int progressPct = computeProgressPct(projection.perSender());
        boolean undoAvailable = computeUndoAvailable(projection);
        log.info(
                "event=cleanup_campaign_status_requested tenantId={} jobId={} progressPct={}"
                        + " undoAvailable={}",
                tenantId,
                jobId,
                progressPct,
                undoAvailable);
        return CampaignStatusResponse.from(projection, undoAvailable, progressPct);
    }

    private static int computeProgressPct(List<PerSenderAttemptProjection> perSender) {
        if (perSender.isEmpty()) {
            return 0;
        }
        long terminalCount =
                perSender.stream()
                        .filter(
                                attempt ->
                                        attempt.state() == UnsubscribeAttemptState.OK
                                                || attempt.state()
                                                        == UnsubscribeAttemptState.FAILED)
                        .count();
        return (int) (terminalCount * 100L / perSender.size());
    }

    private boolean computeUndoAvailable(CampaignStatusProjection projection) {
        if (projection.status() != CampaignStatus.COMPLETED
                || projection.appliedAt() == null
                || projection.revertedAt() != null) {
            return false;
        }
        Instant undoableUntil = UnsubscribeCampaignPolicy.undoableUntil(projection.appliedAt());
        return clock.instant().isBefore(undoableUntil);
    }
}
