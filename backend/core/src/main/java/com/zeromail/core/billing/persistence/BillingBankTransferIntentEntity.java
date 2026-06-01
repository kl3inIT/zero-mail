package com.zeromail.core.billing.persistence;

import com.zeromail.core.shared.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "billing_bank_transfer_intent")
public class BillingBankTransferIntentEntity extends AbstractAuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "plan_code_snapshot", nullable = false, length = 64, updatable = false)
    private String planCodeSnapshot;

    @Column(name = "user_email", length = 320, updatable = false)
    private String userEmail;

    @Column(name = "provider", nullable = false, length = 32, updatable = false)
    private String provider;

    @Column(name = "code", nullable = false, length = 16, updatable = false)
    private String code;

    @Column(name = "amount_vnd", nullable = false, updatable = false)
    private long amountVnd;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "provider_transaction_id", length = 255)
    private String providerTransactionId;

    @Column(name = "bank_code_snapshot", nullable = false, length = 32, updatable = false)
    private String bankCodeSnapshot;

    @Column(name = "bank_name_snapshot", length = 120, updatable = false)
    private String bankNameSnapshot;

    @Column(name = "account_number_snapshot", nullable = false, length = 64, updatable = false)
    private String accountNumberSnapshot;

    @Column(name = "account_name_snapshot", nullable = false, length = 160, updatable = false)
    private String accountNameSnapshot;

    @Column(name = "transfer_content_snapshot", nullable = false, length = 160, updatable = false)
    private String transferContentSnapshot;

    @Column(
            name = "qr_url_snapshot",
            nullable = false,
            columnDefinition = "text",
            updatable = false)
    private String qrUrlSnapshot;

    protected BillingBankTransferIntentEntity() {
        // Hibernate
    }

    public BillingBankTransferIntentEntity(
            UUID id,
            UUID tenantId,
            UUID planId,
            String planCodeSnapshot,
            String userEmail,
            String provider,
            String code,
            long amountVnd,
            String currency,
            String status,
            Instant expiresAt,
            String bankCodeSnapshot,
            String bankNameSnapshot,
            String accountNumberSnapshot,
            String accountNameSnapshot,
            String transferContentSnapshot,
            String qrUrlSnapshot) {
        super(id);
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (planId == null) {
            throw new IllegalArgumentException("planId is required");
        }
        if (amountVnd <= 0) {
            throw new IllegalArgumentException("amountVnd must be positive");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt is required");
        }
        this.tenantId = tenantId;
        this.planId = planId;
        this.planCodeSnapshot = planCodeSnapshot;
        this.userEmail = userEmail;
        this.provider = provider;
        this.code = code;
        this.amountVnd = amountVnd;
        this.currency = currency;
        this.status = status;
        this.expiresAt = expiresAt;
        this.bankCodeSnapshot = bankCodeSnapshot;
        this.bankNameSnapshot = bankNameSnapshot;
        this.accountNumberSnapshot = accountNumberSnapshot;
        this.accountNameSnapshot = accountNameSnapshot;
        this.transferContentSnapshot = transferContentSnapshot;
        this.qrUrlSnapshot = qrUrlSnapshot;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public String getPlanCodeSnapshot() {
        return planCodeSnapshot;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getProvider() {
        return provider;
    }

    public String getCode() {
        return code;
    }

    public long getAmountVnd() {
        return amountVnd;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public String getProviderTransactionId() {
        return providerTransactionId;
    }

    public String getBankCodeSnapshot() {
        return bankCodeSnapshot;
    }

    public String getBankNameSnapshot() {
        return bankNameSnapshot;
    }

    public String getAccountNumberSnapshot() {
        return accountNumberSnapshot;
    }

    public String getAccountNameSnapshot() {
        return accountNameSnapshot;
    }

    public String getTransferContentSnapshot() {
        return transferContentSnapshot;
    }

    public String getQrUrlSnapshot() {
        return qrUrlSnapshot;
    }

    public void markPaid(String providerTransactionId, Instant paidAt) {
        this.status = "PAID";
        this.providerTransactionId = providerTransactionId;
        this.paidAt = paidAt;
    }

    public void markExpired() {
        this.status = "EXPIRED";
    }

    public void markVoided() {
        this.status = "VOIDED";
    }
}
