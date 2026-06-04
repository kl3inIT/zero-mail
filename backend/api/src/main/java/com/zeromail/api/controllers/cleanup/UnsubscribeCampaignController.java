package com.zeromail.api.controllers.cleanup;

import com.zeromail.api.dto.cleanup.CampaignExecuteRequest;
import com.zeromail.api.dto.cleanup.CampaignExecuteResponse;
import com.zeromail.api.error.ErrorTypes;
import com.zeromail.core.cleanup.exception.CampaignCapExceededException;
import com.zeromail.core.cleanup.usecases.CampaignExecuteService;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * UNS-04 execute HTTP surface for the bulk-unsubscribe workflow. Thin controller per CONVENTIONS
 * §1.
 *
 * <p>The Inbox Zero-style UI dropped the preview dialog, the campaign status polling page, the
 * retry button, and the undo banner — the HTTP surface follows suit. The remaining endpoint commits
 * a campaign + N attempts + processing_job inside {@link CampaignExecuteService} and returns the
 * job id for the worker to pick up.
 *
 * <p><b>Exception → HTTP code mapping</b> (controller-local overrides of the {@code
 * GlobalExceptionHandler}'s {@code ErrorClass} defaults):
 *
 * <ul>
 *   <li>{@link CampaignCapExceededException} → 400 (overrides the exception's default 422).
 * </ul>
 *
 * <p>{@link com.zeromail.core.cleanup.exception.SuppressedSenderException} (409) keeps its global
 * mapping — no controller-local handler needed.
 *
 * <p>Privacy invariant (UNS-09): log lines record UUIDs and counts only — sender lists are never
 * iterated into log statements.
 */
@RestController
@Tag(name = "cleanup")
@RequestMapping("/api/unsubscribe/campaigns")
public class UnsubscribeCampaignController {

    private static final Logger log = LoggerFactory.getLogger(UnsubscribeCampaignController.class);

    private final CampaignExecuteService campaignExecuteService;

    public UnsubscribeCampaignController(CampaignExecuteService campaignExecuteService) {
        this.campaignExecuteService = campaignExecuteService;
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

    /**
     * SPEC override: cap violations surface as HTTP 400 even though the {@link
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
