package com.zeromail.api.controllers.cleanup;

import com.zeromail.api.dto.cleanup.UnsubscribeCandidateListResponse;
import com.zeromail.core.cleanup.domain.UnsubscribeCampaignPolicy;
import com.zeromail.core.cleanup.projection.UnsubscribeCandidateProjection;
import com.zeromail.core.cleanup.usecases.CandidateQueryService;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
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
    private static final String DEFAULT_WINDOW_ID = "90d";
    private static final int DEFAULT_LIMIT = 50;

    private final CandidateQueryService candidateQueryService;

    public UnsubscribeCandidateController(CandidateQueryService candidateQueryService) {
        this.candidateQueryService = candidateQueryService;
    }

    @GetMapping("/candidates")
    public UnsubscribeCandidateListResponse listCandidates(
            @RequestParam(value = "window", required = false) String rawWindow,
            @RequestParam(value = "startDate", required = false) String rawStartDate,
            @RequestParam(value = "endDate", required = false) String rawEndDate,
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit) {
        int effectiveLimit =
                Math.min(Math.max(limit, 1), UnsubscribeCampaignPolicy.MAX_CANDIDATE_SENDERS);
        boolean hasStartDate = rawStartDate != null;
        boolean hasEndDate = rawEndDate != null;
        if (hasStartDate != hasEndDate) {
            throw new IllegalArgumentException("startDate and endDate must be supplied together");
        }
        boolean hasRange = hasStartDate;
        List<UnsubscribeCandidateProjection> projections;
        String logWindow;
        UUID tenantId = TenantContext.currentTenantUuid();
        if (hasRange) {
            Instant fromInclusive = parseStartOfUtcDay(rawStartDate);
            Instant toExclusive = parseEndOfUtcDay(rawEndDate);
            if (!fromInclusive.isBefore(toExclusive)) {
                throw new IllegalArgumentException("startDate must be on or before endDate");
            }
            projections =
                    candidateQueryService.findCandidates(
                            tenantId, fromInclusive, toExclusive, effectiveLimit);
            logWindow = rawStartDate + ".." + rawEndDate;
        } else {
            Duration window = resolveWindow(rawWindow);
            projections = candidateQueryService.findCandidates(tenantId, window, effectiveLimit);
            logWindow = window.toString();
        }
        log.info(
                "event=cleanup_candidates_listed tenantId={} window={} limit={} count={}",
                tenantId,
                logWindow,
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

    // Parse a yyyy-MM-dd query param into the UTC instant that starts that calendar day. The
    // picker UI submits the user's local calendar date; representing it as UTC midnight keeps the
    // backend zone-agnostic and lines up with how observed_at is bucketed downstream. Returns
    // 400-equivalent IllegalArgumentException on malformed input — no quiet fallback so users see
    // the validation error instead of getting silently-empty results.
    private static Instant parseStartOfUtcDay(String rawDate) {
        return parseIsoDate(rawDate).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static Instant parseEndOfUtcDay(String rawDate) {
        return parseIsoDate(rawDate).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static LocalDate parseIsoDate(String rawDate) {
        try {
            return LocalDate.parse(rawDate);
        } catch (DateTimeParseException invalidDate) {
            throw new IllegalArgumentException("Date must be yyyy-MM-dd, got: " + rawDate);
        }
    }
}
