package com.zeromail.core.shared.error;

/**
 * Stable, dotted-hierarchy error code constants emitted by the HTTP error pipeline in every Phase 1
 * error response. The frontend switches on these codes (D-C3) and localizes via the {@code
 * errors.*} dictionary namespace; raw exception messages, {@code ProblemDetail.title}, and {@code
 * ProblemDetail.detail} are NEVER user-facing.
 *
 * <p>Naming convention (CONTEXT.md decision D-C3): hierarchical dotted keys, mirroring the {@code
 * messages/{vi,en}.json} {@code errors.*} namespace one-to-one.
 *
 * <p>Lives in {@code core} so business exceptions can attach their own code at construction without
 * the {@code api} package depending on {@code api}. Adapted from the JHipster {@code
 * ErrorConstants} reference (kept: dotted-key style; rejected: {@code error.http.{status}} default
 * codes — Zero Mail uses semantic keys).
 */
public final class ErrorCodes {

    public static final String AUTH_UNAUTHORIZED = "error.auth.unauthorized";
    public static final String AUTH_FORBIDDEN = "error.auth.forbidden";
    public static final String AUTH_CURRENT_USER_NOT_FOUND = "error.auth.currentUserNotFound";
    public static final String VALIDATION = "error.validation";
    public static final String DATA_INTEGRITY = "error.dataIntegrity";
    public static final String CONFLICT = "error.conflict";
    public static final String BAD_REQUEST = "error.badRequest";
    public static final String GMAIL_DISCONNECTED = "error.gmail.disconnected";
    public static final String AUTH_CONSENT_DENIED = "error.auth.consent_denied";
    public static final String AUTH_GMAIL_SCOPE_REQUIRED = "error.auth.gmail_scope_required";
    public static final String BILLING_INSUFFICIENT_CREDITS = "error.billing.insufficient";
    public static final String BILLING_LEDGER_INVALID_STATE = "error.billing.ledger.invalidState";
    public static final String BILLING_SEPAY_REFERENCE_INVALID =
            "error.billing.sepay.reference_invalid";
    public static final String BILLING_SEPAY_AUTH_INVALID = "error.billing.sepay.auth_invalid";
    public static final String LLM_SAFETY_VIOLATION = "error.llm.safety_violation";
    public static final String LLM_SANITIZATION_FAILED = "error.llm.sanitization_failed";
    public static final String LLM_BYOK_INVALID = "error.llm.byok.invalid";
    public static final String LLM_BYOK_VALIDATE_FAILED = "error.llm.byok.validate_failed";
    public static final String RULES_COMPILE_INVALID = "error.rules.compile.invalid";
    public static final String RULES_COMPILE_CLARIFICATION_REQUIRED =
            "error.rules.compile.clarification_required";
    public static final String RULES_NOT_FOUND = "error.rules.not_found";
    public static final String RULES_PREVIEW_REQUIRED = "error.rules.preview.required";
    public static final String RULES_PREVIEW_INVALID_SAMPLE_SIZE =
            "error.rules.preview.invalid_sample_size";
    public static final String RULES_REORDER_INVALID = "error.rules.reorder.invalid";
    public static final String RULES_VERSION_MISMATCH = "error.rules.version_mismatch";
    public static final String RULES_UNSAFE_ACTION = "error.rules.unsafe_action";
    public static final String RULES_DUPLICATE = "error.rules.duplicate";
    public static final String RULES_GMAIL_UNAVAILABLE = "error.rules.gmail.unavailable";
    public static final String TRIAGE_UNDO_EXPIRED = "error.triage.undo.expired";
    public static final String TRIAGE_UNDO_ALREADY_DONE = "error.triage.undo.already_done";
    public static final String TRIAGE_UNDO_UNSUPPORTED_ACTION =
            "error.triage.undo.unsupported_action";
    public static final String TRIAGE_UNDO_WRITE_FAILED = "error.triage.undo.write_failed";
    public static final String TRIAGE_AUDIT_NOT_FOUND = "error.triage.audit.not_found";
    public static final String TRIAGE_SAFETY_VIOLATION = "error.triage.safety_violation";
    public static final String DRAFT_GENERATION_IN_FLIGHT = "error.draft.generation.in_flight";
    public static final String DRAFT_GENERATION_UNAVAILABLE = "error.draft.generation.unavailable";
    public static final String DRAFT_GENERATION_FAILED = "error.draft.generation.failed";
    public static final String INVALID_CURSOR = "error.pagination.invalid_cursor";
    public static final String CAMPAIGN_TOO_MANY_SENDERS =
            "error.cleanup.campaign.too_many_senders";
    public static final String CAMPAIGN_TOO_MANY_MESSAGES =
            "error.cleanup.campaign.too_many_messages";
    public static final String CAMPAIGN_NOT_FOUND = "error.cleanup.campaign.not_found";
    public static final String CAMPAIGN_UNDO_WINDOW_EXPIRED =
            "error.cleanup.campaign.undo_window_expired";
    public static final String CAMPAIGN_RETRY_CONFLICT = "error.cleanup.campaign.retry_conflict";
    public static final String SENDER_SUPPRESSED = "error.cleanup.sender_suppressed";

    private ErrorCodes() {}
}
