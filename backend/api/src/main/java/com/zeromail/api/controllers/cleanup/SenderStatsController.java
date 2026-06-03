package com.zeromail.api.controllers.cleanup;

import com.zeromail.api.dto.cleanup.SenderMessageBodyResponse;
import com.zeromail.api.dto.cleanup.SenderMessageSummaryResponse;
import com.zeromail.api.dto.cleanup.SenderTimelineEntryResponse;
import com.zeromail.core.cleanup.projection.SenderMessageBody;
import com.zeromail.core.cleanup.projection.SenderTimelineEntry;
import com.zeromail.core.cleanup.usecases.SenderMessageReadService;
import com.zeromail.core.cleanup.usecases.SenderTimelineQueryService;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stats dialog HTTP surface for the bulk-unsubscribe workflow (UNS-stats-01..03). Three read-only
 * endpoints powering the Inbox Zero-style sender stats modal:
 *
 * <ul>
 *   <li>{@code GET /api/unsubscribe/stats/timeline?senderEmail=...&windowDays=30} — bar chart per
 *       UTC day, aggregated from {@code mail_message_observed}.
 *   <li>{@code GET /api/unsubscribe/stats/messages?senderEmail=...&archivedOnly=...&limit=...} —
 *       live Gmail list with {@code q=from:<senderEmail>}.
 *   <li>{@code GET /api/unsubscribe/stats/messages/{gmailMessageId}/body} — live Gmail body fetch
 *       for the preview pane. Never persisted server-side.
 * </ul>
 *
 * <p>Privacy invariant (UNS-09 + ARCH-02): no body content is logged at this boundary; the
 * controller logs counts + sender domain only.
 */
@RestController
@Tag(name = "cleanup")
@RequestMapping("/api/unsubscribe/stats")
@Validated
public class SenderStatsController {

    private static final Logger log = LoggerFactory.getLogger(SenderStatsController.class);
    private static final Duration GMAIL_FETCH_BUDGET = Duration.ofSeconds(10);
    private static final int DEFAULT_MESSAGE_LIMIT = 50;
    private static final int MAX_MESSAGE_LIMIT = 100;
    private static final int DEFAULT_WINDOW_DAYS = 30;
    private static final int MAX_WINDOW_DAYS = 365;

    private final SenderTimelineQueryService senderTimelineQueryService;
    private final SenderMessageReadService senderMessageReadService;

    public SenderStatsController(
            SenderTimelineQueryService senderTimelineQueryService,
            SenderMessageReadService senderMessageReadService) {
        this.senderTimelineQueryService = senderTimelineQueryService;
        this.senderMessageReadService = senderMessageReadService;
    }

    @GetMapping("/timeline")
    public List<SenderTimelineEntryResponse> timeline(
            @RequestParam("senderEmail") @NotBlank @Size(max = 320) String senderEmail,
            @RequestParam(name = "windowDays", required = false) Integer windowDays) {
        UUID tenantId = TenantContext.currentTenantUuid();
        int effectiveWindowDays =
                clampWindowDays(windowDays == null ? DEFAULT_WINDOW_DAYS : windowDays);
        List<SenderTimelineEntry> entries =
                senderTimelineQueryService.findTimeline(
                        tenantId, senderEmail, Duration.ofDays(effectiveWindowDays));
        log.info(
                "event=cleanup_sender_stats_timeline tenantId={} windowDays={} bucketCount={}",
                tenantId,
                effectiveWindowDays,
                entries.size());
        return entries.stream().map(SenderTimelineEntryResponse::from).toList();
    }

    @GetMapping("/messages")
    public List<SenderMessageSummaryResponse> messages(
            @RequestParam("senderEmail") @NotBlank @Size(max = 320) String senderEmail,
            @RequestParam(name = "archivedOnly", required = false, defaultValue = "false")
                    boolean archivedOnly,
            @RequestParam(name = "limit", required = false)
                    @Min(1)
                    @Max(MAX_MESSAGE_LIMIT)
                    Integer limit) {
        UUID tenantId = TenantContext.currentTenantUuid();
        int effectiveLimit = limit == null ? DEFAULT_MESSAGE_LIMIT : limit;
        var summaries =
                senderMessageReadService.fetchMessagesFromSender(
                        tenantId, senderEmail, archivedOnly, effectiveLimit, GMAIL_FETCH_BUDGET);
        log.info(
                "event=cleanup_sender_stats_messages tenantId={} archivedOnly={} count={}",
                tenantId,
                archivedOnly,
                summaries.size());
        return summaries.stream().map(SenderMessageSummaryResponse::from).toList();
    }

    @GetMapping("/messages/{gmailMessageId}/body")
    public ResponseEntity<SenderMessageBodyResponse> messageBody(
            @PathVariable("gmailMessageId") @NotBlank @Size(max = 64) String gmailMessageId) {
        UUID tenantId = TenantContext.currentTenantUuid();
        Optional<SenderMessageBody> body =
                senderMessageReadService.fetchMessageBody(
                        tenantId, gmailMessageId, GMAIL_FETCH_BUDGET);
        log.info(
                "event=cleanup_sender_stats_body tenantId={} found={}",
                tenantId,
                body.isPresent());
        return body.map(SenderMessageBodyResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static int clampWindowDays(int requested) {
        if (requested < 1) return 1;
        return Math.min(requested, MAX_WINDOW_DAYS);
    }
}
