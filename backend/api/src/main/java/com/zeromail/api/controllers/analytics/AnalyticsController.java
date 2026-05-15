package com.zeromail.api.controllers.analytics;

import com.zeromail.api.dto.analytics.AnalyticsSummaryResponse;
import com.zeromail.api.dto.analytics.AnalyticsWindow;
import com.zeromail.api.error.ErrorCodes;
import com.zeromail.core.analytics.domain.TimeWindow;
import com.zeromail.core.analytics.projection.AnalyticsSummaryProjection;
import com.zeromail.core.analytics.usecases.AnalyticsSummaryQueryService;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "analytics")
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);
    private static final String DEFAULT_WINDOW_ID = "7d";

    private final AnalyticsSummaryQueryService analyticsSummaryQueryService;

    public AnalyticsController(AnalyticsSummaryQueryService analyticsSummaryQueryService) {
        this.analyticsSummaryQueryService = analyticsSummaryQueryService;
    }

    @GetMapping("/summary")
    public AnalyticsSummaryResponse summary(
            @RequestParam(value = "window", required = false) String rawWindow) {
        AnalyticsWindow analyticsWindow = resolveWindow(rawWindow);
        UUID tenantId = TenantContext.currentTenantUuid();
        TimeWindow timeWindow = TimeWindow.endingAt(Instant.now(), analyticsWindow.duration());
        AnalyticsSummaryProjection projection =
                analyticsSummaryQueryService.summarize(tenantId, timeWindow);
        log.info(
                "event=analytics_summary_requested tenantId={} window={}",
                tenantId,
                analyticsWindow.id());
        return AnalyticsSummaryResponse.from(projection, analyticsWindow);
    }

    @ExceptionHandler(InvalidAnalyticsWindowException.class)
    ResponseEntity<ProblemDetail> invalidWindow(InvalidAnalyticsWindowException exception) {
        log.info(
                "event=analytics_summary_bad_window tenantId={} reason={}",
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Invalid analytics window");
        problemDetail.setDetail("The requested analytics window is not supported.");
        problemDetail.setProperty("code", ErrorCodes.BAD_REQUEST);
        problemDetail.setProperty("params", Map.of());
        problemDetail.setProperty("message", ErrorCodes.BAD_REQUEST);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    private static AnalyticsWindow resolveWindow(String rawWindow) {
        String windowId =
                rawWindow == null || rawWindow.isBlank() ? DEFAULT_WINDOW_ID : rawWindow.trim();
        try {
            return AnalyticsWindow.fromId(windowId);
        } catch (NoSuchElementException invalidWindow) {
            throw new InvalidAnalyticsWindowException(invalidWindow);
        }
    }

    private static String tenantIdForLog() {
        try {
            return TenantContext.currentOrThrow();
        } catch (RuntimeException tenantContextMissing) {
            return "unknown";
        }
    }

    private static final class InvalidAnalyticsWindowException extends RuntimeException {

        private InvalidAnalyticsWindowException(NoSuchElementException cause) {
            super(cause);
        }
    }
}
