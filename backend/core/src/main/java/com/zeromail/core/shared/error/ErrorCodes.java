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
    public static final String GMAIL_MAILBOX_NOT_FOUND = "error.gmail.mailbox.not_found";
    public static final String GMAIL_MAILBOX_DUPLICATE_ACTIVE =
            "error.gmail.mailbox.duplicate_active";
    public static final String CALENDAR_CONNECTION_NOT_FOUND =
            "error.calendar.connection.not_found";
    public static final String CALENDAR_DISCONNECTED = "error.calendar.disconnected";
    public static final String AUTH_CONSENT_DENIED = "error.auth.consent_denied";
    public static final String AUTH_GMAIL_SCOPE_REQUIRED = "error.auth.gmail_scope_required";
    public static final String BILLING_INSUFFICIENT_CREDITS = "error.billing.insufficient";
    public static final String BILLING_LEDGER_INVALID_STATE = "error.billing.ledger.invalidState";
    public static final String BILLING_PLAN_FEATURE_DISABLED = "error.billing.plan.featureDisabled";
    public static final String BILLING_PLAN_NOT_FOUND = "error.billing.plan.notFound";
    public static final String BILLING_CHECKOUT_UNAVAILABLE = "error.billing.checkout.unavailable";
    public static final String BILLING_PLAN_DOWNGRADE_NOT_ALLOWED =
            "error.billing.plan.downgradeNotAllowed";
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
    public static final String SENDER_SUPPRESSED = "error.cleanup.sender_suppressed";
    public static final String VOICE_WRITING_STYLE_TOO_SHORT = "voice.writing_style.too_short";
    public static final String VOICE_WRITING_STYLE_TOO_LONG = "voice.writing_style.too_long";
    public static final String VOICE_PERSONAL_INSTRUCTIONS_TOO_LONG =
            "voice.personal_instructions.too_long";
    public static final String BEHAVIOR_DRAFT_CONFIDENCE_INVALID =
            "behavior.draft_confidence.invalid";
    public static final String KNOWLEDGE_TITLE_DUPLICATE = "knowledge.title.duplicate";
    public static final String KNOWLEDGE_NOT_FOUND = "knowledge.not_found";
    public static final String SAFETY_NET_PATTERN_INVALID = "safety_net.pattern_invalid";
    public static final String SAFETY_NET_OBSERVATION_NOT_DELETABLE =
            "safety_net.observation_not_deletable";
    public static final String SAFETY_NET_NOT_FOUND = "safety_net.not_found";

    private ErrorCodes() {}
}
