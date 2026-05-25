package com.zeromail.core.queue.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * Short, enum-shaped failure-class codes persisted into {@code processing_job.last_failure_reason}.
 *
 * <p>NEVER store raw exception text in {@code last_failure_reason}. Worker error handlers MUST map
 * a {@link Throwable} to a value of this enum BEFORE writing to Postgres. The DB CHECK constraint
 * (Liquibase 078) gives a runtime gate; {@link
 * com.zeromail.core.admin.arch.WorkerFailureReasonEnumOnlyTest} is the compile-time gate that
 * forbids worker code from passing {@code Throwable#getMessage()} through to the column.
 *
 * <p>Adding a new value: also add it to the {@code ck_processing_job_last_failure_reason} CHECK in
 * Liquibase 078 (or a follow-up changeset) so the DB and the enum stay aligned.
 */
public enum JobFailureReason implements IdentifiedEnum {

    /** Downstream HTTP / RPC call timed out (read or connect timeout). */
    DOWNSTREAM_TIMEOUT,

    /** Gmail API quota / rate-limit error (HTTP 429 or 403 with quotaExceeded). */
    GMAIL_API_RATE_LIMIT,

    /**
     * Required encryption / signing key could not be resolved (master key missing, KEK rotation
     * mid-flight).
     */
    ENCRYPTION_KEY_MISSING,

    /** Job payload failed structural validation (schema, constraints). */
    VALIDATION_FAILED,

    /** Provider returned a non-retryable HTTP error (400/401/403/422 from LLM provider). */
    PROVIDER_HTTP_ERROR,

    /** Payload could not be serialized / deserialized — usually a bug, not a transient. */
    SERIALIZATION_FAILED,

    /**
     * Cleanup campaign step was deferred by the per-domain unsubscribe throttle (Phase 8 D-20).
     * Workers re-queue the job with a delayed {@code next_run_at}; this value lands in {@code
     * last_failure_reason} only when the deferral exhausts (campaign capped at max retries) and the
     * job is finally failed, not on the deferral itself.
     */
    THROTTLE_DEFERRED,

    /**
     * Cleanup campaign sender attempted RFC 8058 one-click POST and settled to a terminal
     * non-success (3xx redirect, 4xx, 5xx, timeout, network) after the success window was reached
     * (Phase 8 D-08).
     */
    UNSUBSCRIBE_HTTP_FAILED,

    /**
     * Cleanup campaign sender attempted the {@code mailto:} unsubscribe fallback via Gmail
     * send-as-self and Gmail returned an unrecoverable error (provenance mismatch, Gmail 5xx,
     * etc.).
     */
    UNSUBSCRIBE_MAILTO_FAILED,

    /**
     * Cleanup campaign sender unsubscribed successfully but the post-step bulk archive of historic
     * messages failed irrecoverably (Phase 8 D-04 per-sender atomicity — partial archive is
     * surfaced as a campaign-level failure, not a partial success).
     */
    ARCHIVE_FAILED,

    /** Catch-all bucket for unmapped exceptions; treated as transient by default. */
    UNKNOWN;

    @Override
    public String id() {
        return name();
    }

    public static JobFailureReason fromId(String id) {
        return Stream.of(values())
                .filter(jobFailureReason -> jobFailureReason.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () -> new NoSuchElementException("Unknown JobFailureReason id: " + id));
    }
}
