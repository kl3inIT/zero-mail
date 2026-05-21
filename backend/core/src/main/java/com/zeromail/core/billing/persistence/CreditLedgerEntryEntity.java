package com.zeromail.core.billing.persistence;

import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

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

    @Column(name = "grant_id")
    private UUID grantId;

    protected CreditLedgerEntryEntity() {
        // Hibernate
    }

    private CreditLedgerEntryEntity(
            UUID id, UUID tenantId, String kind, int amountCredits, String refType, String refId) {
        this(id, tenantId, kind, amountCredits, refType, refId, null);
    }

    private CreditLedgerEntryEntity(
            UUID id,
            UUID tenantId,
            String kind,
            int amountCredits,
            String refType,
            String refId,
            UUID grantId) {
        super(id, tenantId);
        this.kind = kind;
        this.amountCredits = amountCredits;
        this.refType = refType;
        this.refId = refId;
        this.grantId = grantId;
    }

    public static CreditLedgerEntryEntity topup(
            UUID id, UUID tenantId, int amountCredits, String sepayTransactionId) {
        return topup(id, tenantId, amountCredits, sepayTransactionId, null);
    }

    public static CreditLedgerEntryEntity topup(
            UUID id, UUID tenantId, int amountCredits, String sepayTransactionId, UUID grantId) {
        if (amountCredits <= 0) {
            throw new IllegalArgumentException("TOPUP amountCredits must be positive");
        }
        return new CreditLedgerEntryEntity(
                id, tenantId, "TOPUP", amountCredits, "PAYMENT_SEPAY", sepayTransactionId, grantId);
    }

    public static CreditLedgerEntryEntity grant(
            UUID id, UUID tenantId, int amountCredits, UUID grantId, String refType, String refId) {
        if (amountCredits <= 0) {
            throw new IllegalArgumentException("GRANT amountCredits must be positive");
        }
        if (grantId == null) {
            throw new IllegalArgumentException("GRANT grantId must be present");
        }
        return new CreditLedgerEntryEntity(
                id, tenantId, "GRANT", amountCredits, refType, refId, grantId);
    }

    public static CreditLedgerEntryEntity reserve(
            UUID id, UUID tenantId, int costCredits, UUID reservationId) {
        return reserve(id, tenantId, costCredits, reservationId, null);
    }

    public static CreditLedgerEntryEntity reserve(
            UUID id, UUID tenantId, int costCredits, UUID reservationId, UUID grantId) {
        if (costCredits <= 0) {
            throw new IllegalArgumentException("RESERVE costCredits must be positive");
        }
        return new CreditLedgerEntryEntity(
                id,
                tenantId,
                "RESERVE",
                -costCredits,
                "RESERVATION",
                reservationId.toString(),
                grantId);
    }

    public static CreditLedgerEntryEntity settle(UUID id, UUID tenantId, UUID reservationId) {
        return new CreditLedgerEntryEntity(
                id, tenantId, "SETTLE", 0, "RESERVATION", reservationId.toString());
    }

    public static CreditLedgerEntryEntity release(
            UUID id, UUID tenantId, int amountCredits, UUID reservationId) {
        return release(id, tenantId, amountCredits, reservationId, null);
    }

    public static CreditLedgerEntryEntity release(
            UUID id, UUID tenantId, int amountCredits, UUID reservationId, UUID grantId) {
        if (amountCredits <= 0) {
            throw new IllegalArgumentException("RELEASE amountCredits must be positive");
        }
        return new CreditLedgerEntryEntity(
                id,
                tenantId,
                "RELEASE",
                amountCredits,
                "RESERVATION",
                reservationId.toString(),
                grantId);
    }

    public static CreditLedgerEntryEntity expire(
            UUID id, UUID tenantId, int amountCredits, UUID grantId, String refId) {
        if (amountCredits <= 0) {
            throw new IllegalArgumentException("EXPIRE amountCredits must be positive");
        }
        if (grantId == null) {
            throw new IllegalArgumentException("EXPIRE grantId must be present");
        }
        return new CreditLedgerEntryEntity(
                id, tenantId, "EXPIRE", -amountCredits, "CREDIT_GRANT", refId, grantId);
    }

    public static CreditLedgerEntryEntity adjustment(
            UUID id, UUID tenantId, int amountCredits, UUID grantId, String refType, String refId) {
        if (amountCredits == 0) {
            throw new IllegalArgumentException("ADJUSTMENT amountCredits must be non-zero");
        }
        return new CreditLedgerEntryEntity(
                id, tenantId, "ADJUSTMENT", amountCredits, refType, refId, grantId);
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

    public UUID getGrantId() {
        return grantId;
    }
}
