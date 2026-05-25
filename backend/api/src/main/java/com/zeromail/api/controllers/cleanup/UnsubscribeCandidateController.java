package com.zeromail.api.controllers.cleanup;

import com.zeromail.api.dto.cleanup.UnsubscribeCandidateListResponse;
import com.zeromail.core.cleanup.domain.UnsubscribeCampaignPolicy;
import com.zeromail.core.cleanup.projection.UnsubscribeCandidateProjection;
import com.zeromail.core.cleanup.usecases.CandidateQueryService;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * UNS-01 — list candidate senders with usable {@code List-Unsubscribe} headers observed in the
 * requested window. Thin controller (CONVENTIONS §1): extract tenant context, resolve window string
 * to {@link Duration}, delegate to {@link CandidateQueryService}, map projections to wire DTO.
 *
 * <p>Privacy invariant (UNS-09): the access log records the result count only — never the sender
 * list itself.
 */
@RestController
@Tag(name = "cleanup")
@RequestMapping("/api/unsubscribe")
public class UnsubscribeCandidateController {

    private static final Logger log = LoggerFactory.getLogger(UnsubscribeCandidateController.class);
    private static final String DEFAULT_WINDOW_ID = "30d";
    private static final int DEFAULT_LIMIT = 25;

    private final CandidateQueryService candidateQueryService;

    public UnsubscribeCandidateController(CandidateQueryService candidateQueryService) {
        this.candidateQueryService = candidateQueryService;
    }

    @GetMapping("/candidates")
    public UnsubscribeCandidateListResponse listCandidates(
            @RequestParam(value = "window", required = false) String rawWindow,
            @RequestParam(value = "limit", required = false, defaultValue = "25") int limit) {
        UUID tenantId = TenantContext.currentTenantUuid();
        Duration window = resolveWindow(rawWindow);
        int effectiveLimit =
                Math.min(Math.max(limit, 1), UnsubscribeCampaignPolicy.MAX_SENDERS_PER_CAMPAIGN);
        List<UnsubscribeCandidateProjection> projections =
                candidateQueryService.findCandidates(tenantId, window, effectiveLimit);
        log.info(
                "event=cleanup_candidates_listed tenantId={} window={} limit={} count={}",
                tenantId,
                window,
                effectiveLimit,
                projections.size());
        return UnsubscribeCandidateListResponse.from(projections);
    }

    private static Duration resolveWindow(String rawWindow) {
        String windowId =
                rawWindow == null || rawWindow.isBlank() ? DEFAULT_WINDOW_ID : rawWindow.trim();
        return switch (windowId) {
            case "7d" -> Duration.ofDays(7);
            case "30d" -> Duration.ofDays(30);
            case "90d" -> Duration.ofDays(90);
            default -> throw new IllegalArgumentException("Unsupported window: " + windowId);
        };
    }
}
