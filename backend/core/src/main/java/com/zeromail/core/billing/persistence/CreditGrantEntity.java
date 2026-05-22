package com.zeromail.core.billing.persistence;

import com.zeromail.core.billing.domain.CreditGrantCategory;
import com.zeromail.core.billing.domain.CreditGrantStatus;
import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_grant")
public class CreditGrantEntity extends AbstractTenantOwnedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 24)
    private CreditGrantCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CreditGrantStatus status;

    @Column(name = "amount_credits", nullable = false)
    private int amountCredits;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "ref_type", nullable = false, length = 32)
    private String refType;

    @Column(name = "ref_id", nullable = false, length = 128)
    private String refId;

    protected CreditGrantEntity() {
        // Hibernate
    }

    public CreditGrantEntity(
            UUID id,
            UUID tenantId,
            CreditGrantCategory category,
            CreditGrantStatus status,
            int amountCredits,
            Instant effectiveAt,
            Instant expiresAt,
            int priority,
            String refType,
            String refId) {
        super(id, tenantId);
        if (amountCredits <= 0) {
            throw new IllegalArgumentException("amountCredits must be positive");
        }
        if (effectiveAt == null) {
            throw new IllegalArgumentException("effectiveAt is required");
        }
        if (expiresAt != null && !expiresAt.isAfter(effectiveAt)) {
            throw new IllegalArgumentException("expiresAt must be after effectiveAt");
        }
        if (refType == null || refType.isBlank()) {
            throw new IllegalArgumentException("refType is required");
        }
        if (refId == null || refId.isBlank()) {
            throw new IllegalArgumentException("refId is required");
        }
        this.category = category;
        this.status = status;
        this.amountCredits = amountCredits;
        this.effectiveAt = effectiveAt;
        this.expiresAt = expiresAt;
        this.priority = priority;
        this.refType = refType;
        this.refId = refId;
    }

    public CreditGrantCategory getCategory() {
        return category;
    }

    public CreditGrantStatus getStatus() {
        return status;
    }

    public int getAmountCredits() {
        return amountCredits;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getPriority() {
        return priority;
    }

    public String getRefType() {
        return refType;
    }

    public String getRefId() {
        return refId;
    }

    public void markDepleted() {
        status = CreditGrantStatus.DEPLETED;
    }

    public void markExpired() {
        status = CreditGrantStatus.EXPIRED;
    }

    public void markVoided() {
        status = CreditGrantStatus.VOIDED;
    }
}
