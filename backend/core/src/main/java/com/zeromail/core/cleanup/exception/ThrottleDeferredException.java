package com.zeromail.core.cleanup.exception;

import java.util.UUID;

/**
 * Signal exception thrown by {@code UnsubscribeCampaignHandler} when the per-tenant per-domain
 * throttle bucket (D-20) refuses an unsubscribe attempt. The handler MUST first re-queue the
 * underlying {@code processing_job} row (set {@code status='PENDING'} + {@code next_run_at = NOW()
 * + 60s}) so that on the next poll cycle the job is re-picked up by {@code ProcessingJobWorker}.
 *
 * <p>{@code ProcessingJobWorker.dispatchJob} catches this exception <b>before</b> the generic
 * {@code RuntimeException} branch so the row is NOT overwritten with {@code status='FAILED'} — see
 * {@code ProcessingJobWorkerThrottleDeferralTest} (M-2 fix).
 *
 * <p>Canonical {@code reason} strings: {@code "PER_DOMAIN_60S_EXCEEDED"} and {@code
 * "PER_DOMAIN_1H_EXCEEDED"}.
 */
public final class ThrottleDeferredException extends RuntimeException {

    private final UUID tenantId;
    private final String senderDomain;
    private final String reason;

    public ThrottleDeferredException(UUID tenantId, String senderDomain, String reason) {
        super(
                "Throttle deferred: tenantId="
                        + tenantId
                        + " senderDomain="
                        + senderDomain
                        + " reason="
                        + reason);
        this.tenantId = tenantId;
        this.senderDomain = senderDomain;
        this.reason = reason;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String senderDomain() {
        return senderDomain;
    }

    public String reason() {
        return reason;
    }
}
