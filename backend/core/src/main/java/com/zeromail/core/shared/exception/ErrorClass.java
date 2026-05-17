package com.zeromail.core.shared.exception;

/**
 * HTTP-semantic category attached to every {@link BusinessException}. Lets {@code core} business
 * exceptions declare their intent without depending on Spring's {@code HttpStatus} type; the HTTP
 * adapter ({@code api.config.GlobalExceptionHandler}) maps each value to a concrete status code.
 */
public enum ErrorClass {

    /** Malformed request payload or arguments — HTTP 400. */
    BAD_REQUEST,

    /** Authentication missing or invalid — HTTP 401. */
    UNAUTHORIZED,

    /** Out of credit / payment required — HTTP 402. */
    PAYMENT_REQUIRED,

    /** Authenticated but not allowed — HTTP 403. */
    FORBIDDEN,

    /** Resource not found — HTTP 404. */
    NOT_FOUND,

    /** State conflict (idempotency, optimistic lock, undoable state) — HTTP 409. */
    CONFLICT,

    /** Well-formed request that cannot be processed semantically — HTTP 422. */
    UNPROCESSABLE,

    /** Internal invariant violated or downstream programming error — HTTP 500. */
    INTERNAL,

    /** Upstream / write-side dependency failed — HTTP 502. */
    GATEWAY_FAILURE,

    /** Transient dependency unavailability — HTTP 503. */
    SERVICE_UNAVAILABLE
}
