package com.zeromail.api.config;

import com.zeromail.api.error.AllowedParamScalars;
import com.zeromail.api.error.ErrorCodes;
import com.zeromail.api.error.FieldErrorDto;
import com.zeromail.api.error.InvalidCursorException;
import com.zeromail.api.error.RuleApiException;
import com.zeromail.core.account.exception.CurrentUserNotFoundException;
import com.zeromail.core.billing.exception.IllegalLedgerStateException;
import com.zeromail.core.billing.exception.InsufficientCreditsException;
import com.zeromail.core.draft.exception.DraftGenerationFailedException;
import com.zeromail.core.draft.exception.DraftGenerationInFlightException;
import com.zeromail.core.draft.exception.DraftGenerationUnavailableException;
import com.zeromail.core.llm.exception.InvalidByokException;
import com.zeromail.core.llm.exception.SafetyViolationException;
import com.zeromail.core.llm.exception.SanitizationException;
import com.zeromail.core.rules.exception.GmailPreviewUnavailableException;
import com.zeromail.core.rules.exception.RuleValidationException;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.triage.exception.MissingMessageIdException;
import com.zeromail.core.triage.exception.ThreadingHeaderInvalidException;
import com.zeromail.core.triage.exception.TriageAuditException;
import com.zeromail.core.triage.exception.TriageAuditNotFoundException;
import com.zeromail.core.triage.exception.TriageSafetyViolationException;
import com.zeromail.core.triage.exception.TriageUndoAlreadyDoneException;
import com.zeromail.core.triage.exception.TriageUndoExpiredException;
import com.zeromail.core.triage.exception.TriageUndoWriteFailedException;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Centralized exception translation. All client-visible failures are returned as RFC 7807 {@link
 * ProblemDetail} bodies extended with the locked Phase 1.1 contract:
 *
 * <pre>
 * {
 *   "type":   "about:blank",
 *   "title":  "...",      // generic English diagnostic — NOT user-facing
 *   "status": 4xx,
 *   "detail": "...",      // generic English diagnostic — NOT user-facing
 *   "code":   "error.auth.unauthorized",   // FE switches on this (D-C3 dotted)
 *   "params": {},                          // allow-listed scalars only (D-C2)
 *   "fieldErrors": [ ... ],                // for validation only
 *   "message": "error.auth.unauthorized"   // transitional alias of code (drop next phase)
 * }
 * </pre>
 *
 * <p><b>Why {@code extends ResponseEntityExceptionHandler}?</b> Under {@code
 * spring.mvc.problemdetails.enabled=true} the framework's default {@code
 * MethodArgumentNotValidException} handling silently bypasses
 * {@code @ExceptionHandler(MethodArgumentNotValidException.class)} on a plain
 * {@code @RestControllerAdvice}. Inheriting {@link ResponseEntityExceptionHandler} and overriding
 * {@link #handleMethodArgumentNotValid} is the supported route. (Mitigates threat T-1.1.02-02; cite
 * RESEARCH.md Pitfall 1; Spring issue #35982.)
 *
 * <p><b>Privacy invariants:</b>
 *
 * <ul>
 *   <li>{@code title} / {@code detail} are generic English diagnostics — they MUST not contain raw
 *       user input, exception class names, SQL constraint names, refresh-token shapes, or stack
 *       traces.
 *   <li>{@code params} values pass through {@link AllowedParamScalars#filter} — any rejected user
 *       input is silently dropped before reaching the wire.
 *   <li>The handler logs the underlying exception class on the server side only.
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CurrentUserNotFoundException.class)
    public ResponseEntity<ProblemDetail> onCurrentUserMissing(
            CurrentUserNotFoundException exception) {
        log.warn(
                "event=current_user_missing tenantId={} reason={}",
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        return problem(
                HttpStatus.UNAUTHORIZED,
                "Current user is not available",
                "The authenticated session points at a user that no longer exists.",
                ErrorCodes.AUTH_CURRENT_USER_NOT_FOUND);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> onAuthFailed(AuthenticationException exception) {
        log.warn(
                "event=auth_failure tenantId={} reason={}",
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        return problem(
                HttpStatus.UNAUTHORIZED,
                "Authentication required",
                "The request requires an authenticated session.",
                ErrorCodes.AUTH_UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> onAccessDenied(AccessDeniedException exception) {
        log.warn(
                "event=access_denied tenantId={} reason={}",
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        return problem(
                HttpStatus.FORBIDDEN,
                "Access denied",
                "The current principal is not permitted to perform this action.",
                ErrorCodes.AUTH_FORBIDDEN);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> onDataIntegrity(
            DataIntegrityViolationException exception) {
        // Logging only the exception class name, never the throwable itself: Logback would
        // render the wrapped SQLException's message + SQL state into the appender, leaking
        // schema details into logs.
        log.warn(
                "event=data_integrity_conflict tenantId={} reason={}",
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        return problem(
                HttpStatus.CONFLICT,
                "Conflict",
                "The request could not be persisted because a data integrity rule was violated.",
                ErrorCodes.DATA_INTEGRITY);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> onOptimisticLock(
            OptimisticLockingFailureException exception) {
        log.warn(
                "event=optimistic_lock_conflict tenantId={} reason={}",
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        return problem(
                HttpStatus.CONFLICT,
                "Conflict",
                "The resource was modified by another request before this update completed.",
                ErrorCodes.CONFLICT);
    }

    @ExceptionHandler(InsufficientCreditsException.class)
    public ResponseEntity<ProblemDetail> onInsufficientCredits(
            InsufficientCreditsException exception) {
        log.warn(
                "event=insufficient_credits tenantId={} reason={}",
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        return problem(
                HttpStatus.valueOf(402),
                "Insufficient credits",
                "The current tenant balance is too low for this action.",
                ErrorCodes.BILLING_INSUFFICIENT_CREDITS);
    }

    @ExceptionHandler(IllegalLedgerStateException.class)
    public ResponseEntity<ProblemDetail> onIllegalLedgerState(
            IllegalLedgerStateException exception) {
        log.error(
                "event=illegal_ledger_state tenantId={} reason={}",
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ledger state invariant violated",
                "An internal billing-state transition was attempted in an invalid order.",
                ErrorCodes.BILLING_LEDGER_INVALID_STATE);
    }

    @ExceptionHandler(SafetyViolationException.class)
    public ResponseEntity<ProblemDetail> onSafetyViolation(SafetyViolationException exception) {
        log.error(
                "event=llm_safety_violation tenantId={} reason={}",
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        return problem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "LLM safety violation",
                "The model response violated the LLM safety policy.",
                ErrorCodes.LLM_SAFETY_VIOLATION);
    }

    @ExceptionHandler(SanitizationException.class)
    public ResponseEntity<ProblemDetail> onSanitizationFailed(SanitizationException exception) {
        log.error(
                "event=llm_sanitization_failed tenantId={} reason={}",
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "LLM sanitization failed",
                "The LLM request could not be sanitized safely.",
                ErrorCodes.LLM_SANITIZATION_FAILED);
    }

    @ExceptionHandler(InvalidByokException.class)
    public ResponseEntity<ProblemDetail> onInvalidByok(InvalidByokException exception) {
        log.warn(
                "event=llm_byok_invalid tenantId={} reason={}",
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid BYOK credentials",
                "The BYOK provider, endpoint, or key could not be validated.",
                ErrorCodes.LLM_BYOK_INVALID);
    }

    @ExceptionHandler(RuleApiException.class)
    public ResponseEntity<ProblemDetail> onRuleApiException(RuleApiException exception) {
        log.warn(
                "event=rules_api_rejected tenantId={} reason={}",
                tenantIdForLog(),
                exception.reason());
        return switch (exception.reason()) {
            case INVALID_COMPILE_OUTPUT ->
                    problem(
                            HttpStatus.BAD_REQUEST,
                            "Invalid rule compile output",
                            "The compiled rule payload is invalid.",
                            ErrorCodes.RULES_COMPILE_INVALID);
            case CLARIFICATION_REQUIRED ->
                    problem(
                            HttpStatus.BAD_REQUEST,
                            "Rule clarification required",
                            "The rule must be clarified before this operation can continue.",
                            ErrorCodes.RULES_COMPILE_CLARIFICATION_REQUIRED);
            case INVALID_SAMPLE_SIZE ->
                    problem(
                            HttpStatus.BAD_REQUEST,
                            "Invalid preview sample size",
                            "Preview sample size must be one of the allowed values.",
                            ErrorCodes.RULES_PREVIEW_INVALID_SAMPLE_SIZE);
            case INVALID_REORDER ->
                    problem(
                            HttpStatus.BAD_REQUEST,
                            "Invalid rule order",
                            "The reorder request must include the full current rule list.",
                            ErrorCodes.RULES_REORDER_INVALID);
            case UNSAFE_ACTION ->
                    problem(
                            HttpStatus.BAD_REQUEST,
                            "Unsafe rule action",
                            "The rule contains an action outside the safe action allow-list.",
                            ErrorCodes.RULES_UNSAFE_ACTION);
        };
    }

    @ExceptionHandler(RuleValidationException.class)
    public ResponseEntity<ProblemDetail> onRuleValidation(RuleValidationException exception) {
        log.warn(
                "event=rules_validation_rejected tenantId={} reason={}",
                tenantIdForLog(),
                exception.reason());
        return switch (exception.reason()) {
            case NOT_FOUND ->
                    problem(
                            HttpStatus.NOT_FOUND,
                            "Rule not found",
                            "The requested rule was not found for the current tenant.",
                            ErrorCodes.RULES_NOT_FOUND);
            case PREVIEW_REQUIRED ->
                    problem(
                            HttpStatus.CONFLICT,
                            "Rule preview required",
                            "The current rule version must be previewed before enabling.",
                            ErrorCodes.RULES_PREVIEW_REQUIRED);
            case VERSION_MISMATCH ->
                    problem(
                            HttpStatus.CONFLICT,
                            "Rule version mismatch",
                            "The rule version changed before the request completed.",
                            ErrorCodes.RULES_VERSION_MISMATCH);
            case INVALID_REORDER ->
                    problem(
                            HttpStatus.BAD_REQUEST,
                            "Invalid rule order",
                            "The reorder request must include the full current rule list.",
                            ErrorCodes.RULES_REORDER_INVALID);
            case UNSAFE_ACTION ->
                    problem(
                            HttpStatus.BAD_REQUEST,
                            "Unsafe rule action",
                            "The rule contains an action outside the safe action allow-list.",
                            ErrorCodes.RULES_UNSAFE_ACTION);
        };
    }

    @ExceptionHandler(GmailPreviewUnavailableException.class)
    public ResponseEntity<ProblemDetail> onGmailPreviewUnavailable(
            GmailPreviewUnavailableException exception) {
        log.warn(
                "event=rules_gmail_preview_unavailable tenantId={} reason={}",
                tenantIdForLog(),
                exception.reason().id());
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Gmail preview unavailable",
                "Gmail preview data is not currently available.",
                ErrorCodes.RULES_GMAIL_UNAVAILABLE,
                Map.of("reason", exception.reason().id()));
    }

    @ExceptionHandler(TriageUndoExpiredException.class)
    public ResponseEntity<ProblemDetail> onTriageUndoExpired(TriageUndoExpiredException exception) {
        log.warn(
                "event=triage_undo_rejected tenantId={} reason={}",
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        return problem(
                HttpStatus.CONFLICT,
                "Triage undo expired",
                "The triage action can no longer be undone.",
                ErrorCodes.TRIAGE_UNDO_EXPIRED);
    }

    @ExceptionHandler(TriageUndoAlreadyDoneException.class)
    public ResponseEntity<ProblemDetail> onTriageUndoAlreadyDone(
            TriageUndoAlreadyDoneException exception) {
        log.warn(
                "event=triage_undo_rejected tenantId={} reason={}",
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        return problem(
                HttpStatus.CONFLICT,
                "Triage undo unavailable",
                "The triage action is not in an undoable state.",
                ErrorCodes.TRIAGE_UNDO_ALREADY_DONE);
    }

    @ExceptionHandler(TriageAuditException.class)
    public ResponseEntity<ProblemDetail> onTriageAuditException(TriageAuditException exception) {
        log.warn(
                "event=triage_audit_rejected tenantId={} reason={}",
                tenantIdForLog(),
                exception.reason());
        return switch (exception.reason()) {
            case UNSUPPORTED_ACTION_TYPE ->
                    problem(
                            HttpStatus.CONFLICT,
                            "Triage undo unsupported",
                            "The triage action type cannot be undone safely.",
                            ErrorCodes.TRIAGE_UNDO_UNSUPPORTED_ACTION);
        };
    }

    @ExceptionHandler(TriageAuditNotFoundException.class)
    public ResponseEntity<ProblemDetail> onTriageAuditNotFound(
            TriageAuditNotFoundException exception) {
        log.warn(
                "event=triage_audit_not_found tenantId={} reason={}",
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        return problem(
                HttpStatus.NOT_FOUND,
                "Triage audit not found",
                "The requested triage audit entry was not found.",
                ErrorCodes.TRIAGE_AUDIT_NOT_FOUND);
    }

    @ExceptionHandler(TriageUndoWriteFailedException.class)
    public ResponseEntity<ProblemDetail> onTriageUndoWriteFailed(
            TriageUndoWriteFailedException exception) {
        log.error(
                "event=triage_undo_write_failed tenantId={} reason={}",
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        return problem(
                HttpStatus.BAD_GATEWAY,
                "Triage undo write failed",
                "The triage action could not be undone right now.",
                ErrorCodes.TRIAGE_UNDO_WRITE_FAILED);
    }

    @ExceptionHandler(TriageSafetyViolationException.class)
    public ResponseEntity<ProblemDetail> onTriageSafetyViolation(
            TriageSafetyViolationException exception) {
        log.error(
                "event=triage_safety_violation tenantId={} reason={}",
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Triage safety violation",
                "The triage action violated the safety policy.",
                ErrorCodes.TRIAGE_SAFETY_VIOLATION);
    }

    @ExceptionHandler(DraftGenerationInFlightException.class)
    public ResponseEntity<ProblemDetail> onDraftGenerationInFlight(
            DraftGenerationInFlightException exception) {
        log.warn(
                "event=draft_generation_rejected tenantId={} reason={}",
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        return problem(
                HttpStatus.CONFLICT,
                "Draft generation already in flight",
                "A draft is already being generated for this thread.",
                ErrorCodes.DRAFT_GENERATION_IN_FLIGHT);
    }

    @ExceptionHandler(DraftGenerationUnavailableException.class)
    public ResponseEntity<ProblemDetail> onDraftGenerationUnavailable(
            DraftGenerationUnavailableException exception) {
        log.warn(
                "event=draft_generation_unavailable tenantId={} reason={}",
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Draft generation temporarily unavailable",
                "Draft generation is temporarily unavailable. Try again later.",
                ErrorCodes.DRAFT_GENERATION_UNAVAILABLE);
    }

    @ExceptionHandler({
        DraftGenerationFailedException.class,
        MissingMessageIdException.class,
        ThreadingHeaderInvalidException.class
    })
    public ResponseEntity<ProblemDetail> onDraftGenerationFailed(RuntimeException exception) {
        log.warn(
                "event=draft_generation_failed tenantId={} reason={}",
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        return problem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Draft generation failed",
                "A draft could not be generated for this thread.",
                ErrorCodes.DRAFT_GENERATION_FAILED);
    }

    @ExceptionHandler(InvalidCursorException.class)
    public ResponseEntity<ProblemDetail> onInvalidCursor(InvalidCursorException exception) {
        log.warn(
                "event=invalid_cursor tenantId={} reason={}",
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid cursor",
                "The pagination cursor is malformed.",
                ErrorCodes.INVALID_CURSOR);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> onIllegalState(IllegalStateException exception) {
        log.warn(
                "event=illegal_state tenantId={} reason={}",
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        return problem(
                HttpStatus.CONFLICT,
                "Conflict",
                "The request could not be completed in the current resource state.",
                ErrorCodes.CONFLICT);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> onIllegalArg(IllegalArgumentException exception) {
        log.warn(
                "event=illegal_argument tenantId={} reason={}",
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        return problem(
                HttpStatus.BAD_REQUEST,
                "Bad request",
                "The request is malformed or contains an invalid argument.",
                ErrorCodes.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> onConstraintViolation(
            ConstraintViolationException exception) {
        // jakarta.validation path-style violations on @Validated method parameters /
        // path variables. Mirrors the @MethodArgumentNotValid shape: same code, same
        // fieldErrors[] array, with field paths like "method.argName".
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Validation failed");
        problemDetail.setDetail("One or more parameters failed validation.");
        problemDetail.setProperty("code", ErrorCodes.VALIDATION);
        problemDetail.setProperty("params", Map.of());
        problemDetail.setProperty(
                "fieldErrors",
                exception.getConstraintViolations().stream()
                        .map(
                                constraintViolation ->
                                        new FieldErrorDto(
                                                constraintViolation.getPropertyPath().toString(),
                                                "error.validation.field."
                                                        + constraintViolation
                                                                .getPropertyPath()
                                                                .toString()
                                                        + "."
                                                        + constraintViolation
                                                                .getConstraintDescriptor()
                                                                .getAnnotation()
                                                                .annotationType()
                                                                .getSimpleName(),
                                                AllowedParamScalars.filter(new Object[0])))
                        .toList());
        problemDetail.setProperty("message", ErrorCodes.VALIDATION);
        log.warn(
                "event=validation_rejected tenantId={} fieldErrors={}",
                tenantIdForLog(),
                exception.getConstraintViolations().size());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Validation failed");
        problemDetail.setDetail("One or more fields failed validation.");
        problemDetail.setProperty("code", ErrorCodes.VALIDATION);
        problemDetail.setProperty("params", Map.of());
        problemDetail.setProperty(
                "fieldErrors",
                exception.getBindingResult().getFieldErrors().stream()
                        .map(
                                fieldError ->
                                        new FieldErrorDto(
                                                fieldError.getField(),
                                                "error.validation.field."
                                                        + fieldError.getField()
                                                        + "."
                                                        + fieldError.getCode(),
                                                AllowedParamScalars.filter(
                                                        fieldError.getArguments())))
                        .toList());
        problemDetail.setProperty("message", ErrorCodes.VALIDATION);
        log.warn(
                "event=validation_rejected tenantId={} fieldErrors={}",
                tenantIdForLog(),
                exception.getBindingResult().getFieldErrorCount());
        return handleExceptionInternal(exception, problemDetail, headers, status, request);
    }

    /**
     * Build a ProblemDetail with the locked Phase 1.1 extension members.
     *
     * @param status HTTP status — drives both the response status and {@code ProblemDetail.status}.
     * @param title generic English diagnostic for RFC 7807 / operator logs (NEVER user-facing).
     * @param detail generic English diagnostic for RFC 7807 / operator logs (NEVER user-facing).
     * @param code dotted hierarchical key the FE switches on (see {@link ErrorCodes}).
     */
    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String title, String detail, String code) {
        return problem(status, title, detail, code, Map.of());
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String title,
            String detail,
            String code,
            Map<String, Object> params) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);
        problemDetail.setProperty("code", code);
        problemDetail.setProperty("params", params);
        problemDetail.setProperty(
                "message", code); // transitional alias of code (drop next phase per D-C1)
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
