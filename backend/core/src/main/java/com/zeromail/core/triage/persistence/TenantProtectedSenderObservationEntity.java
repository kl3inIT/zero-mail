package com.zeromail.core.triage.persistence;

import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_protected_sender_observation")
public class TenantProtectedSenderObservationEntity extends AbstractTenantOwnedEntity {

    @Column(name = "sender_email", nullable = false, length = 320)
    private String senderEmail;

    @Column(name = "first_observed_at", nullable = false)
    private Instant firstObservedAt;

    @Column(name = "last_observed_at", nullable = false)
    private Instant lastObservedAt;

    @Column(name = "observation_count", nullable = false)
    private int observationCount;

    protected TenantProtectedSenderObservationEntity() {
        // Hibernate
    }

    public TenantProtectedSenderObservationEntity(
            UUID id, UUID tenantId, String senderEmail, Instant observedAt) {
        super(id, tenantId);
        Instant effectiveObservedAt = observedAt == null ? Instant.now() : observedAt;
        this.senderEmail = requireText(senderEmail, "senderEmail");
        this.firstObservedAt = effectiveObservedAt;
        this.lastObservedAt = effectiveObservedAt;
        this.observationCount = 1;
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public Instant getFirstObservedAt() {
        return firstObservedAt;
    }

    public Instant getLastObservedAt() {
        return lastObservedAt;
    }

    public int getObservationCount() {
        return observationCount;
    }

    public void recordObservation(Instant observedAt) {
        lastObservedAt = observedAt == null ? Instant.now() : observedAt;
        observationCount++;
    }

    private static String requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
    }
}
