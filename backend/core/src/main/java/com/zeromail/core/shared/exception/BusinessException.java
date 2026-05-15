package com.zeromail.core.shared.exception;

import java.util.Map;

/**
 * Base class for every business exception the HTTP layer translates into an RFC 7807 ProblemDetail
 * (Pattern B, JHipster v8 style).
 *
 * <p>Carrying the full contract (HTTP class, dotted error code, log event name, English title /
 * detail diagnostics, structured params) on the exception itself collapses ~25 hand-written
 * {@code @ExceptionHandler} bodies down to one. The HTTP adapter ({@code
 * api.config.GlobalExceptionHandler}) only needs the {@link ErrorClass} → status map; every other
 * field rides on the exception.
 *
 * <p><b>Privacy invariants</b> (mirrored from the handler contract):
 *
 * <ul>
 *   <li>{@link #title()} and {@link #detail()} are generic English diagnostics — they MUST NOT
 *       contain raw user input, exception class names, SQL constraint names, refresh-token shapes,
 *       email content, prompt/completion bytes, or stack traces.
 *   <li>{@link #params()} entries are passed through {@code AllowedParamScalars#filter} before
 *       reaching the wire; only short identifiers / counters survive.
 *   <li>{@link #logEvent()} is the {@code event=<name>} segment of the privacy log format. No
 *       per-tenant identifiers belong here.
 * </ul>
 */
public abstract class BusinessException extends RuntimeException {

    protected BusinessException() {
        super();
    }

    protected BusinessException(String message) {
        super(message);
    }

    protected BusinessException(String message, Throwable cause) {
        super(message, cause);
    }

    protected BusinessException(Throwable cause) {
        super(null, cause);
    }

    /** HTTP-semantic category — drives the status code in the response. */
    public abstract ErrorClass errorClass();

    /**
     * Stable dotted error code the frontend switches on (e.g. {@code error.billing.insufficient}).
     */
    public abstract String errorCode();

    /** Privacy log {@code event=<name>} segment (e.g. {@code insufficient_credits}). */
    public abstract String logEvent();

    /** Generic English diagnostic for RFC 7807 {@code title} — NEVER user-facing. */
    public abstract String title();

    /** Generic English diagnostic for RFC 7807 {@code detail} — NEVER user-facing. */
    public abstract String detail();

    /**
     * Allow-list-filtered structured params attached to the ProblemDetail. Default is empty;
     * override only when the wire payload genuinely needs a scalar (e.g. a reason id) for the
     * frontend to localize.
     */
    public Map<String, Object> params() {
        return Map.of();
    }
}
