package com.zeromail.api.config;

import java.util.Map;

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

import com.zeromail.api.error.AllowedParamScalars;
import com.zeromail.api.error.ErrorCodes;
import com.zeromail.api.error.FieldErrorDto;
import com.zeromail.core.account.CurrentUserNotFoundException;

import jakarta.validation.ConstraintViolationException;

/**
 * Centralized exception translation. All client-visible failures are returned as
 * RFC 7807 {@link ProblemDetail} bodies extended with the locked Phase 1.1 contract:
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
 * <p><b>Why {@code extends ResponseEntityExceptionHandler}?</b> Under
 * {@code spring.mvc.problemdetails.enabled=true} the framework's default
 * {@code MethodArgumentNotValidException} handling silently bypasses
 * {@code @ExceptionHandler(MethodArgumentNotValidException.class)} on a plain
 * {@code @RestControllerAdvice}. Inheriting {@link ResponseEntityExceptionHandler}
 * and overriding {@link #handleMethodArgumentNotValid} is the supported route.
 * (Mitigates threat T-1.1.02-02; cite RESEARCH.md Pitfall 1; Spring issue #35982.)
 *
 * <p><b>Privacy invariants:</b>
 * <ul>
 *   <li>{@code title} / {@code detail} are generic English diagnostics — they MUST
 *       not contain raw user input, exception class names, SQL constraint names,
 *       refresh-token shapes, or stack traces.</li>
 *   <li>{@code params} values pass through {@link AllowedParamScalars#filter} —
 *       any rejected user input is silently dropped before reaching the wire.</li>
 *   <li>The handler logs the underlying exception class on the server side only.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CurrentUserNotFoundException.class)
    public ResponseEntity<ProblemDetail> onCurrentUserMissing(CurrentUserNotFoundException ex) {
        log.warn("Current user not found for tenant; rejecting with 401");
        return problem(HttpStatus.UNAUTHORIZED,
                "Current user is not available",
                "The authenticated session points at a user that no longer exists.",
                ErrorCodes.AUTH_CURRENT_USER_NOT_FOUND);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> onAuthFailed(AuthenticationException ex) {
        log.warn("Authentication failure: {}", ex.getClass().getSimpleName());
        return problem(HttpStatus.UNAUTHORIZED,
                "Authentication required",
                "The request requires an authenticated session.",
                ErrorCodes.AUTH_UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> onAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getClass().getSimpleName());
        return problem(HttpStatus.FORBIDDEN,
                "Access denied",
                "The current principal is not permitted to perform this action.",
                ErrorCodes.AUTH_FORBIDDEN);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> onDataIntegrity(DataIntegrityViolationException ex) {
        // Do NOT echo SQL state, constraint names, or raw exception messages to the client —
        // those can leak schema details. Log server-side instead.
        log.warn("Data integrity violation translated to 409", ex);
        return problem(HttpStatus.CONFLICT,
                "Conflict",
                "The request could not be persisted because a data integrity rule was violated.",
                ErrorCodes.DATA_INTEGRITY);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> onOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Optimistic lock failure translated to 409: {}", ex.getClass().getSimpleName());
        return problem(HttpStatus.CONFLICT,
                "Conflict",
                "The resource was modified by another request before this update completed.",
                ErrorCodes.CONFLICT);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> onIllegalState(IllegalStateException ex) {
        log.warn("IllegalStateException translated to 409: {}", ex.getClass().getSimpleName());
        return problem(HttpStatus.CONFLICT,
                "Conflict",
                "The request could not be completed in the current resource state.",
                ErrorCodes.CONFLICT);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> onIllegalArg(IllegalArgumentException ex) {
        log.warn("IllegalArgumentException translated to 400: {}", ex.getClass().getSimpleName());
        return problem(HttpStatus.BAD_REQUEST,
                "Bad request",
                "The request is malformed or contains an invalid argument.",
                ErrorCodes.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> onConstraintViolation(ConstraintViolationException ex) {
        // jakarta.validation path-style violations on @Validated method parameters /
        // path variables. Mirrors the @MethodArgumentNotValid shape: same code, same
        // fieldErrors[] array, with field paths like "method.argName".
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Validation failed");
        pd.setDetail("One or more parameters failed validation.");
        pd.setProperty("code", ErrorCodes.VALIDATION);
        pd.setProperty("params", Map.of());
        pd.setProperty("fieldErrors",
                ex.getConstraintViolations().stream()
                        .map(cv -> new FieldErrorDto(
                                cv.getPropertyPath().toString(),
                                "error.validation.field." + cv.getPropertyPath().toString()
                                        + "." + cv.getConstraintDescriptor().getAnnotation()
                                                  .annotationType().getSimpleName(),
                                AllowedParamScalars.filter(new Object[0])))
                        .toList());
        pd.setProperty("message", ErrorCodes.VALIDATION);
        log.warn("ConstraintViolationException translated to 400 with {} field error(s)",
                ex.getConstraintViolations().size());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Validation failed");
        pd.setDetail("One or more fields failed validation.");
        pd.setProperty("code", ErrorCodes.VALIDATION);
        pd.setProperty("params", Map.of());
        pd.setProperty("fieldErrors",
                ex.getBindingResult().getFieldErrors().stream()
                        .map(fe -> new FieldErrorDto(
                                fe.getField(),
                                "error.validation.field." + fe.getField() + "." + fe.getCode(),
                                AllowedParamScalars.filter(fe.getArguments())))
                        .toList());
        pd.setProperty("message", ErrorCodes.VALIDATION);
        log.warn("MethodArgumentNotValid translated to 400 with {} field error(s)",
                ex.getBindingResult().getFieldErrorCount());
        return handleExceptionInternal(ex, pd, headers, status, request);
    }

    /**
     * Build a ProblemDetail with the locked Phase 1.1 extension members.
     *
     * @param status HTTP status — drives both the response status and {@code ProblemDetail.status}.
     * @param title  generic English diagnostic for RFC 7807 / operator logs (NEVER user-facing).
     * @param detail generic English diagnostic for RFC 7807 / operator logs (NEVER user-facing).
     * @param code   dotted hierarchical key the FE switches on (see {@link ErrorCodes}).
     */
    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String title, String detail, String code) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setTitle(title);
        pd.setDetail(detail);
        pd.setProperty("code", code);
        pd.setProperty("params", Map.of());
        pd.setProperty("message", code); // transitional alias of code (drop next phase per D-C1)
        return ResponseEntity.status(status).body(pd);
    }
}
