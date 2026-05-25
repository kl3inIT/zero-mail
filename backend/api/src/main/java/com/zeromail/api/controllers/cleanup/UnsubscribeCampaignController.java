package com.zeromail.api.controllers.cleanup;

import com.zeromail.api.dto.cleanup.CampaignExecuteRequest;
import com.zeromail.api.dto.cleanup.CampaignExecuteResponse;
import com.zeromail.api.dto.cleanup.CampaignPreviewRequest;
import com.zeromail.api.dto.cleanup.CampaignPreviewResponse;
import com.zeromail.api.error.ErrorTypes;
import com.zeromail.core.cleanup.exception.CampaignCapExceededException;
import com.zeromail.core.cleanup.exception.CampaignNotFoundException;
import com.zeromail.core.cleanup.exception.UndoWindowExpiredException;
import com.zeromail.core.cleanup.projection.CampaignStatusProjection;
import com.zeromail.core.cleanup.usecases.CampaignExecuteService;
import com.zeromail.core.cleanup.usecases.CampaignPreviewService;
import com.zeromail.core.cleanup.usecases.CampaignRetryService;
import com.zeromail.core.cleanup.usecases.CampaignStatusQueryService;
import com.zeromail.core.cleanup.usecases.CampaignUndoService;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * UNS-03 (preview) + UNS-04 (execute) + UNS-06 (retry) + UNS-07a (undo) HTTP surface for the
 * bulk-unsubscribe campaign workflow. Thin controller per CONVENTIONS §1.
 *
 * <p>Status polling (UNS-05) lives in {@link CampaignStatusController} — split out so the read-only
 * polling endpoint can carry its own controller-local cache headers and validation tests without
 * dragging the four write-path services along.
 *
 * <p><b>Exception → HTTP code mapping</b> (controller-local overrides of {@code
 * GlobalExceptionHandler}'s {@code ErrorClass} defaults — required by SPEC must_haves):
 *
 * <ul>
 *   <li>{@link CampaignCapExceededException} → 400 (SPEC overrides the exception's default 422).
 *   <li>{@link UndoWindowExpiredException} → 410 (SPEC overrides the exception's default 409).
 * </ul>
 *
 * {@link com.zeromail.core.cleanup.exception.CampaignRetryConflictException} (409), {@link
 * CampaignNotFoundException} (404), and {@link
 * com.zeromail.core.cleanup.exception.SuppressedSenderException} (409) keep their global mapping —
 * no controller-local handler needed.
 *
 * <p>Privacy invariant (UNS-09): log lines record UUIDs and counts only — sender lists are never
 * iterated into log statements.
 */
@RestController
@Tag(name = "cleanup")
@RequestMapping("/api/unsubscribe/campaigns")
public class UnsubscribeCampaignController {

    private static final Logger log = LoggerFactory.getLogger(UnsubscribeCampaignController.class);

    private final CampaignPreviewService campaignPreviewService;
    private final CampaignExecuteService campaignExecuteService;
    private final CampaignRetryService campaignRetryService;
    private final CampaignUndoService campaignUndoService;
    private final CampaignStatusQueryService campaignStatusQueryService;

    public UnsubscribeCampaignController(
            CampaignPreviewService campaignPreviewService,
            CampaignExecuteService campaignExecuteService,
            CampaignRetryService campaignRetryService,
            CampaignUndoService campaignUndoService,
            CampaignStatusQueryService campaignStatusQueryService) {
        this.campaignPreviewService = campaignPreviewService;
        this.campaignExecuteService = campaignExecuteService;
        this.campaignRetryService = campaignRetryService;
        this.campaignUndoService = campaignUndoService;
        this.campaignStatusQueryService = campaignStatusQueryService;
    }

    @PostMapping("/preview")
    public CampaignPreviewResponse preview(@Valid @RequestBody CampaignPreviewRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        CampaignPreviewService.CampaignPreviewResult result =
                campaignPreviewService.preview(tenantId, request.senderEmails());
        log.info(
                "event=cleanup_campaign_preview_requested tenantId={} senderCount={}",
                tenantId,
                request.senderEmails().size());
        return CampaignPreviewResponse.from(result);
    }

    @PostMapping("/execute")
    @ResponseStatus(HttpStatus.CREATED)
    public CampaignExecuteResponse execute(@Valid @RequestBody CampaignExecuteRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        CampaignExecuteService.CampaignExecuteResult result =
                campaignExecuteService.execute(tenantId, request.senderEmails());
        log.info(
                "event=cleanup_campaign_execute_requested tenantId={} campaignId={} jobId={}"
                        + " senderCount={}",
                tenantId,
                result.campaignId(),
                result.jobId(),
                request.senderEmails().size());
        return CampaignExecuteResponse.from(result);
    }

    @PostMapping("/{jobId}/senders/{senderEmail}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void retrySender(@PathVariable UUID jobId, @PathVariable String senderEmail) {
        UUID tenantId = TenantContext.currentTenantUuid();
        String decodedSenderEmail = URLDecoder.decode(senderEmail, StandardCharsets.UTF_8);
        campaignRetryService.retry(tenantId, jobId, decodedSenderEmail);
        log.info("event=cleanup_campaign_retry_requested tenantId={} jobId={}", tenantId, jobId);
    }

    @PostMapping("/{jobId}/undo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void undo(@PathVariable UUID jobId) {
        UUID tenantId = TenantContext.currentTenantUuid();
        CampaignStatusProjection projection =
                campaignStatusQueryService
                        .findByJobId(tenantId, jobId)
                        .orElseThrow(() -> new CampaignNotFoundException(jobId));
        CampaignUndoService.CampaignUndoResult result =
                campaignUndoService.undo(tenantId, projection.id());
        log.info(
                "event=cleanup_campaign_undo_requested tenantId={} jobId={} campaignId={}"
                        + " restoredCount={}",
                tenantId,
                jobId,
                projection.id(),
                result.restoredCount());
    }

    /**
     * UNS-03 override: SPEC requires HTTP 400 for cap violations even though the {@link
     * CampaignCapExceededException} carries {@code ErrorClass.UNPROCESSABLE} (which the global
     * handler would map to 422). The dotted {@code code} and {@code params} payload mirror the
     * global handler's {@code ProblemDetail} shape.
     */
    @ExceptionHandler(CampaignCapExceededException.class)
    public ResponseEntity<ProblemDetail> handleCapExceeded(CampaignCapExceededException exception) {
        log.warn(
                "event={} tenantId={} reason={} kind={} actual={} cap={}",
                exception.logEvent(),
                tenantIdForLog(),
                exception.getClass().getSimpleName(),
                exception.kind().name(),
                exception.actual(),
                exception.cap());
        return problem(
                HttpStatus.BAD_REQUEST,
                exception.title(),
                exception.detail(),
                exception.errorCode(),
                exception.params());
    }

    /**
     * UNS-07b override: SPEC requires HTTP 410 Gone past the 30-day undo window even though the
     * {@link UndoWindowExpiredException} carries {@code ErrorClass.CONFLICT} (which the global
     * handler would map to 409). 410 communicates "the resource you want to restore is no longer
     * eligible for restoration" with sharper semantics for the frontend.
     */
    @ExceptionHandler(UndoWindowExpiredException.class)
    public ResponseEntity<ProblemDetail> handleUndoWindowExpired(
            UndoWindowExpiredException exception) {
        log.warn(
                "event={} tenantId={} reason={}",
                exception.logEvent(),
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        return problem(
                HttpStatus.GONE,
                exception.title(),
                exception.detail(),
                exception.errorCode(),
                exception.params());
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String title,
            String detail,
            String code,
            java.util.Map<String, Object> params) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setType(ErrorTypes.forStatus(status));
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);
        problemDetail.setProperty("code", code);
        problemDetail.setProperty("params", params);
        problemDetail.setProperty("message", code);
        return ResponseEntity.status(status).body(problemDetail);
    }

    private static String tenantIdForLog() {
        try {
            return TenantContext.currentOrThrow();
        } catch (RuntimeException tenantContextMissing) {
            return "unknown";
        }
    }
}
