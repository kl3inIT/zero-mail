package com.zeromail.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.zeromail.core.account.CurrentUserNotFoundException;

/**
 * Centralized exception translation. All client-visible failures are returned as
 * RFC 7807 {@link ProblemDetail} bodies with stable {@code message} keys (i18n-friendly,
 * codegen-friendly) instead of raw exception messages — so the wire shape never leaks
 * persistence/provider/internal-state strings.
 *
 * <p>Mapping policy:
 * <ul>
 *   <li>{@link CurrentUserNotFoundException} -> 401 (the session points at a user that no
 *       longer exists; the previous behavior incorrectly mapped this to 409).</li>
 *   <li>{@link AuthenticationException} -> 401.</li>
 *   <li>{@link AccessDeniedException} -> 403.</li>
 *   <li>{@link IllegalArgumentException} -> 400 with a generic {@code error.badRequest} key.</li>
 *   <li>{@link IllegalStateException} -> 409 with a generic {@code error.conflict} key.</li>
 *   <li>{@link DataIntegrityViolationException} -> 409 with {@code error.dataIntegrity}.</li>
 * </ul>
 *
 * <p>Server-side, we log the underlying exception (without raw user input echoed back to
 * clients) so the on-call still has a diagnostic trail.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CurrentUserNotFoundException.class)
    public ResponseEntity<ProblemDetail> onCurrentUserMissing(CurrentUserNotFoundException ex) {
        log.warn("Current user not found for tenant; rejecting with 401");
        return problem(HttpStatus.UNAUTHORIZED,
                "Current user is not available",
                "error.currentUserNotFound");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> onAuthFailed(AuthenticationException ex) {
        log.warn("Authentication failure: {}", ex.getClass().getSimpleName());
        return problem(HttpStatus.UNAUTHORIZED, "Authentication required", "error.unauthorized");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> onAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getClass().getSimpleName());
        return problem(HttpStatus.FORBIDDEN, "Access denied", "error.forbidden");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> onDataIntegrity(DataIntegrityViolationException ex) {
        // Do NOT echo SQL state, constraint names, or raw exception messages to the client —
        // those can leak schema details. Log server-side instead.
        log.warn("Data integrity violation translated to 409", ex);
        return problem(HttpStatus.CONFLICT, "Conflict", "error.dataIntegrity");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> onIllegalState(IllegalStateException ex) {
        log.warn("IllegalStateException translated to 409: {}", ex.getClass().getSimpleName());
        return problem(HttpStatus.CONFLICT, "Conflict", "error.conflict");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> onIllegalArg(IllegalArgumentException ex) {
        log.warn("IllegalArgumentException translated to 400: {}", ex.getClass().getSimpleName());
        return problem(HttpStatus.BAD_REQUEST, "Bad request", "error.badRequest");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String messageKey) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setProperty("message", messageKey);
        return ResponseEntity.status(status).body(problem);
    }
}
