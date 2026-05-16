package com.zeromail.api.config;

import com.zeromail.api.error.AllowedParamScalars;
import com.zeromail.api.error.FieldErrorDto;
import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import com.zeromail.core.tenant.TenantContext;
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
 * <p><b>Pattern B (JHipster v9 style):</b> business exceptions carry their own HTTP-semantic class
 * ({@link ErrorClass}), dotted error code, log event name, and title/detail diagnostics via {@link
 * BusinessException}. A single {@link #onBusinessException} handler translates every subtype; the
 * remaining handlers cover framework exceptions ({@link AuthenticationException}, {@link
 * AccessDeniedException}, JPA conflicts, validation, etc.) that we do not own.
 *
 * <p><b>Why {@code extends ResponseEntityExceptionHandler}?</b> Under {@code
 * spring.mvc.problemdetails.enabled=true} the framework's default {@code
 * MethodArgumentNotValidException} handling silently bypasses {@code @ExceptionHandler} on a plain
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

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> onBusinessException(BusinessException exception) {
        HttpStatus status = toHttpStatus(exception.errorClass());
        if (status.is5xxServerError()) {
            log.error(
                    "event={} tenantId={} reason={}",
                    exception.logEvent(),
                    tenantIdForLog(),
                    exception.getClass().getSimpleName());
        } else {
            log.warn(
                    "event={} tenantId={} reason={}",
                    exception.logEvent(),
                    tenantIdForLog(),
                    exception.getClass().getSimpleName());
        }
        return problem(
                status,
                exception.title(),
                exception.detail(),
                exception.errorCode(),
                exception.params());
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

    private static HttpStatus toHttpStatus(ErrorClass errorClass) {
        return switch (errorClass) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case PAYMENT_REQUIRED -> HttpStatus.valueOf(402);
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
            case GATEWAY_FAILURE -> HttpStatus.BAD_GATEWAY;
            case SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
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
