package com.zeromail.core.billing.persistence;

import java.time.Instant;
import java.util.UUID;

import com.zeromail.core.billing.domain.BillingTopupIntentStatus;
import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "billing_topup_intent")
public class BillingTopupIntentEntity extends AbstractTenantOwnedEntity {

    @Column(name = "code", nullable = false, length = 16, unique = true)
    private String code;

    @Column(name = "amount_vnd", nullable = false)
    private long amountVnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BillingTopupIntentStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "sepay_transaction_id", length = 128)
    private String sepayTransactionId;

    protected BillingTopupIntentEntity() {
        // Hibernate
    }

    public BillingTopupIntentEntity(
            UUID id,
            UUID tenantId,
            String code,
            long amountVnd,
            BillingTopupIntentStatus status,
            Instant expiresAt) {
        super(id, tenantId);
        this.code = code;
        this.amountVnd = amountVnd;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public String getCode() {
        return code;
    }

    public long getAmountVnd() {
        return amountVnd;
    }

    public BillingTopupIntentStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public String getSepayTransactionId() {
        return sepayTransactionId;
    }

    public void markPaid(String sepayTransactionId) {
        status = BillingTopupIntentStatus.PAID;
        paidAt = Instant.now();
        this.sepayTransactionId = sepayTransactionId;
    }

    public void markExpired() {
        status = BillingTopupIntentStatus.EXPIRED;
    }
}
