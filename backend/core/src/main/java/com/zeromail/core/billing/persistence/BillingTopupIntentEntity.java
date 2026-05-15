package com.zeromail.core.billing.persistence;

import com.zeromail.core.billing.domain.BillingTopupIntentStatus;
import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "billing_topup_intent")
public class BillingTopupIntentEntity extends AbstractTenantOwnedEntity {

    @Column(name = "code", nullable = false, length = 16, unique = true)
    private String code;

    @Column(name = "amount_vnd", nullable = false)
    private long amountVnd;

    @Column(name = "package_id")
    private UUID packageId;

    @Column(name = "package_code_snapshot", length = 64)
    private String packageCodeSnapshot;

    @Column(name = "package_name_snapshot", length = 120)
    private String packageNameSnapshot;

    @Column(name = "credit_amount_snapshot")
    private Integer creditAmountSnapshot;

    @Column(name = "bank_code_snapshot", length = 32)
    private String bankCodeSnapshot;

    @Column(name = "bank_name_snapshot", length = 120)
    private String bankNameSnapshot;

    @Column(name = "account_number_snapshot", length = 64)
    private String accountNumberSnapshot;

    @Column(name = "account_name_snapshot", length = 160)
    private String accountNameSnapshot;

    @Column(name = "transfer_content_snapshot", length = 160)
    private String transferContentSnapshot;

    @Column(name = "qr_payload_snapshot")
    private String qrPayloadSnapshot;

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

    public BillingTopupIntentEntity(
            UUID id,
            UUID tenantId,
            String code,
            long amountVnd,
            UUID packageId,
            String packageCodeSnapshot,
            String packageNameSnapshot,
            int creditAmountSnapshot,
            BillingTopupIntentStatus status,
            Instant expiresAt,
            String bankCodeSnapshot,
            String bankNameSnapshot,
            String accountNumberSnapshot,
            String accountNameSnapshot,
            String transferContentSnapshot,
            String qrPayloadSnapshot) {
        this(id, tenantId, code, amountVnd, status, expiresAt);
        this.packageId = packageId;
        this.packageCodeSnapshot = packageCodeSnapshot;
        this.packageNameSnapshot = packageNameSnapshot;
        this.creditAmountSnapshot = creditAmountSnapshot;
        this.bankCodeSnapshot = bankCodeSnapshot;
        this.bankNameSnapshot = bankNameSnapshot;
        this.accountNumberSnapshot = accountNumberSnapshot;
        this.accountNameSnapshot = accountNameSnapshot;
        this.transferContentSnapshot = transferContentSnapshot;
        this.qrPayloadSnapshot = qrPayloadSnapshot;
    }

    public String getCode() {
        return code;
    }

    public long getAmountVnd() {
        return amountVnd;
    }

    public UUID getPackageId() {
        return packageId;
    }

    public String getPackageCodeSnapshot() {
        return packageCodeSnapshot;
    }

    public String getPackageNameSnapshot() {
        return packageNameSnapshot;
    }

    public Integer getCreditAmountSnapshot() {
        return creditAmountSnapshot;
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

    public String getQrPayloadSnapshot() {
        return qrPayloadSnapshot;
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
