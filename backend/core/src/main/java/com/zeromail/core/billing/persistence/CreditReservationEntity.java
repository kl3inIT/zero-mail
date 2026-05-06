package com.zeromail.core.billing.persistence;

import java.time.Instant;
import java.util.UUID;

import com.zeromail.core.billing.model.CallSite;
import com.zeromail.core.billing.model.CreditReservationStatus;
import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "credit_reservation")
public class CreditReservationEntity extends AbstractTenantOwnedEntity {

    @Column(name = "amount_credits", nullable = false)
    private int amountCredits;

    @Enumerated(EnumType.STRING)
    @Column(name = "call_site", nullable = false, length = 16)
    private CallSite callSite;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CreditReservationStatus status;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    protected CreditReservationEntity() {
        // Hibernate
    }

    public CreditReservationEntity(
            UUID id,
            UUID tenantId,
            int amountCredits,
            CallSite callSite,
            CreditReservationStatus status) {
        super(id, tenantId);
        this.amountCredits = amountCredits;
        this.callSite = callSite;
        this.status = status;
    }

    public int getAmountCredits() {
        return amountCredits;
    }

    public CallSite getCallSite() {
        return callSite;
    }

    public CreditReservationStatus getStatus() {
        return status;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public void markSettled() {
        status = CreditReservationStatus.SETTLED;
        finalizedAt = Instant.now();
    }

    public void markReleased() {
        status = CreditReservationStatus.RELEASED;
        finalizedAt = Instant.now();
    }
}
