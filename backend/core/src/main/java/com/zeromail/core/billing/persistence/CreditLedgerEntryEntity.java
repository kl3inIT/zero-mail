package com.zeromail.core.billing.persistence;

import java.util.UUID;

import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Append-only journal row. Created through static factories so the kind/sign invariant stays
 * centralized in one place.
 */
@Entity
@Table(name = "credit_ledger_entry")
public class CreditLedgerEntryEntity extends AbstractTenantOwnedEntity {

    @Column(name = "kind", nullable = false, length = 16)
    private String kind;

    @Column(name = "amount_credits", nullable = false)
    private int amountCredits;

    @Column(name = "ref_type", nullable = false, length = 32)
    private String refType;

    @Column(name = "ref_id", nullable = false, length = 128)
    private String refId;

    protected CreditLedgerEntryEntity() {
        // Hibernate
    }

    private CreditLedgerEntryEntity(
            UUID id,
            UUID tenantId,
            String kind,
            int amountCredits,
            String refType,
            String refId) {
        super(id, tenantId);
        this.kind = kind;
        this.amountCredits = amountCredits;
        this.refType = refType;
        this.refId = refId;
    }

    public static CreditLedgerEntryEntity topup(UUID id, UUID tenantId, int amountCredits, String sepayTransactionId) {
        if (amountCredits <= 0) {
            throw new IllegalArgumentException("TOPUP amountCredits must be positive");
        }
        return new CreditLedgerEntryEntity(id, tenantId, "TOPUP", amountCredits, "PAYMENT_SEPAY", sepayTransactionId);
    }

    public static CreditLedgerEntryEntity reserve(UUID id, UUID tenantId, int costCredits, UUID reservationId) {
        if (costCredits <= 0) {
            throw new IllegalArgumentException("RESERVE costCredits must be positive");
        }
        return new CreditLedgerEntryEntity(id, tenantId, "RESERVE", -costCredits, "RESERVATION", reservationId.toString());
    }

    public static CreditLedgerEntryEntity settle(UUID id, UUID tenantId, UUID reservationId) {
        return new CreditLedgerEntryEntity(id, tenantId, "SETTLE", 0, "RESERVATION", reservationId.toString());
    }

    public static CreditLedgerEntryEntity release(UUID id, UUID tenantId, int amountCredits, UUID reservationId) {
        if (amountCredits <= 0) {
            throw new IllegalArgumentException("RELEASE amountCredits must be positive");
        }
        return new CreditLedgerEntryEntity(id, tenantId, "RELEASE", amountCredits, "RESERVATION", reservationId.toString());
    }

    public String getKind() {
        return kind;
    }

    public int getAmountCredits() {
        return amountCredits;
    }

    public String getRefType() {
        return refType;
    }

    public String getRefId() {
        return refId;
    }
}
