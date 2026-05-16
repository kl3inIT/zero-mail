package com.zeromail.core.billing.persistence;

import com.zeromail.core.billing.domain.PaymentAttemptStatus;
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
@Table(name = "billing_payment_attempt")
public class BillingPaymentAttemptEntity extends AbstractTenantOwnedEntity {

    @Column(name = "topup_intent_id", nullable = false)
    private UUID topupIntentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private PaymentAttemptStatus status;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "provider_reference_id", length = 255)
    private String providerReferenceId;

    @Column(name = "provider_payment_id", length = 255)
    private String providerPaymentId;

    @Column(name = "redirect_url")
    private String redirectUrl;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_message", length = 256)
    private String failureMessage;

    protected BillingPaymentAttemptEntity() {
        // Hibernate
    }

    public BillingPaymentAttemptEntity(
            UUID id,
            UUID tenantId,
            UUID topupIntentId,
            PaymentProvider provider,
            PaymentAttemptStatus status,
            long amount,
            String currency,
            String providerReferenceId,
            String providerPaymentId,
            String redirectUrl,
            Instant expiresAt,
            Instant completedAt,
            String failureCode,
            String failureMessage) {
        super(id, tenantId);
        this.topupIntentId = topupIntentId;
        this.provider = provider;
        this.status = status;
        this.amount = amount;
        this.currency = currency;
        this.providerReferenceId = providerReferenceId;
        this.providerPaymentId = providerPaymentId;
        this.redirectUrl = redirectUrl;
        this.expiresAt = expiresAt;
        this.completedAt = completedAt;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    public static BillingPaymentAttemptEntity sepayPending(
            UUID id,
            UUID tenantId,
            UUID topupIntentId,
            long amountVnd,
            String orderCode,
            Instant expiresAt) {
        return new BillingPaymentAttemptEntity(
                id,
                tenantId,
                topupIntentId,
                PaymentProvider.SEPAY,
                PaymentAttemptStatus.PENDING,
                amountVnd,
                "vnd",
                orderCode,
                null,
                null,
                expiresAt,
                null,
                null,
                null);
    }

    public UUID getTopupIntentId() {
        return topupIntentId;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public PaymentAttemptStatus getStatus() {
        return status;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getProviderReferenceId() {
        return providerReferenceId;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void markSucceeded(String providerPaymentId) {
        status = PaymentAttemptStatus.SUCCEEDED;
        this.providerPaymentId = providerPaymentId;
        completedAt = Instant.now();
        failureCode = null;
        failureMessage = null;
    }

    public void markExpired() {
        status = PaymentAttemptStatus.EXPIRED;
    }

    public void markFailed(String failureCode, String failureMessage) {
        status = PaymentAttemptStatus.FAILED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }
}
