package com.zeromail.api.error;

/**
 * Stable, dotted-hierarchy error code constants emitted by {@code GlobalExceptionHandler}
 * in every Phase 1 error response. The frontend switches on these codes (D-C3) and
 * localizes via the {@code errors.*} dictionary namespace; raw exception messages,
 * {@code ProblemDetail.title}, and {@code ProblemDetail.detail} are NEVER user-facing.
 *
 * <p>Naming convention (CONTEXT.md decision D-C3): hierarchical dotted keys, mirroring
 * the {@code messages/{vi,en}.json} {@code errors.*} namespace one-to-one.
 *
 * <p>Adapted from the JHipster {@code ErrorConstants} reference (kept: dotted-key style;
 * rejected: {@code error.http.{status}} default codes — Zero Mail uses semantic keys).
 */
public final class ErrorCodes {

    public static final String AUTH_UNAUTHORIZED            = "error.auth.unauthorized";
    public static final String AUTH_FORBIDDEN               = "error.auth.forbidden";
    public static final String AUTH_CURRENT_USER_NOT_FOUND  = "error.auth.currentUserNotFound";
    public static final String VALIDATION                   = "error.validation";
    public static final String DATA_INTEGRITY              = "error.dataIntegrity";
    public static final String CONFLICT                     = "error.conflict";
    public static final String BAD_REQUEST                  = "error.badRequest";
    public static final String GMAIL_DISCONNECTED            = "error.gmail.disconnected";
    public static final String AUTH_CONSENT_DENIED           = "error.auth.consent_denied";
    public static final String AUTH_GMAIL_SCOPE_REQUIRED     = "error.auth.gmail_scope_required";

    private ErrorCodes() {}
}
