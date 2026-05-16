package com.zeromail.core.billing.persistence;

import com.zeromail.core.billing.domain.PaymentEventProcessingStatus;
import com.zeromail.core.billing.domain.PaymentProvider;
import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "billing_payment_event")
public class BillingPaymentEventEntity extends AbstractTenantOwnedEntity {

    @Column(name = "payment_attempt_id", nullable = false)
    private UUID paymentAttemptId;

    @Column(name = "topup_intent_id", nullable = false)
    private UUID topupIntentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private PaymentProvider provider;

    @Column(name = "provider_event_id", nullable = false, length = 255)
    private String providerEventId;

    @Column(name = "event_type", nullable = false, length = 96)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 24)
    private PaymentEventProcessingStatus processingStatus;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected BillingPaymentEventEntity() {
        // Hibernate
    }

    public BillingPaymentEventEntity(
            UUID id,
            UUID tenantId,
            UUID paymentAttemptId,
            UUID topupIntentId,
            PaymentProvider provider,
            String providerEventId,
            String eventType,
            PaymentEventProcessingStatus processingStatus,
            Instant receivedAt,
            Instant processedAt) {
        super(id, tenantId);
        this.paymentAttemptId = paymentAttemptId;
        this.topupIntentId = topupIntentId;
        this.provider = provider;
        this.providerEventId = providerEventId;
        this.eventType = eventType;
        this.processingStatus = processingStatus;
        this.receivedAt = receivedAt;
        this.processedAt = processedAt;
    }

    public UUID getPaymentAttemptId() {
        return paymentAttemptId;
    }

    public UUID getTopupIntentId() {
        return topupIntentId;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public PaymentEventProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
