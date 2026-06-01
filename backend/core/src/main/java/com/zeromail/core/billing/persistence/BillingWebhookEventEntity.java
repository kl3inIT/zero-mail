package com.zeromail.core.billing.persistence;

import com.zeromail.core.shared.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only audit + idempotency row for a single Lemon Squeezy webhook delivery. */
@Entity
@Table(name = "billing_webhook_event")
public class BillingWebhookEventEntity extends AbstractEntity {

    @Column(name = "provider_event_id", length = 255)
    private String providerEventId;

    @Column(name = "dedupe_key", nullable = false, length = 255, updatable = false)
    private String dedupeKey;

    @Column(name = "event_name", nullable = false, length = 64, updatable = false)
    private String eventName;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "lemon_squeezy_order_id")
    private Long lemonSqueezyOrderId;

    @Column(name = "signature_verified", nullable = false, updatable = false)
    private boolean signatureVerified;

    @Column(name = "processing_status", nullable = false, length = 32)
    private String processingStatus;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "processing_error", columnDefinition = "text")
    private String processingError;

    @Column(name = "payload_sha256", nullable = false, length = 64, updatable = false)
    private String payloadSha256;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_jsonb", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payloadJsonb;

    protected BillingWebhookEventEntity() {
        // Hibernate
    }

    public BillingWebhookEventEntity(
            UUID id,
            String providerEventId,
            String dedupeKey,
            String eventName,
            UUID tenantId,
            Long lemonSqueezyOrderId,
            boolean signatureVerified,
            String processingStatus,
            Instant receivedAt,
            String payloadSha256,
            String payloadJsonb) {
        super(id);
        this.providerEventId = providerEventId;
        this.dedupeKey = dedupeKey;
        this.eventName = eventName;
        this.tenantId = tenantId;
        this.lemonSqueezyOrderId = lemonSqueezyOrderId;
        this.signatureVerified = signatureVerified;
        this.processingStatus = processingStatus;
        this.receivedAt = receivedAt;
        this.payloadSha256 = payloadSha256;
        this.payloadJsonb = payloadJsonb;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public String getEventName() {
        return eventName;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public Long getLemonSqueezyOrderId() {
        return lemonSqueezyOrderId;
    }

    public boolean isSignatureVerified() {
        return signatureVerified;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public String getProcessingError() {
        return processingError;
    }

    public String getPayloadSha256() {
        return payloadSha256;
    }

    public String getPayloadJsonb() {
        return payloadJsonb;
    }

    public void markProcessing() {
        this.processingStatus = "PROCESSING";
    }

    public void markProcessed(Instant processedAt) {
        this.processingStatus = "PROCESSED";
        this.processedAt = processedAt;
        this.processingError = null;
    }

    public void markFailed(Instant processedAt, String error) {
        this.processingStatus = "FAILED";
        this.processedAt = processedAt;
        this.processingError = error;
    }

    public void markSkipped(Instant processedAt, String reason) {
        this.processingStatus = "SKIPPED";
        this.processedAt = processedAt;
        this.processingError = reason;
    }
}
