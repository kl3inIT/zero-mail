package com.zeromail.core.triage.persistence;

import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Stream;

@Entity
@Table(name = "tenant_protected_sender_observation")
@SuppressWarnings({"JpaDataSourceORMInspection", "unused"})
public class TenantProtectedSenderObservationEntity extends AbstractTenantOwnedEntity {

    @Column(name = "sender_email", nullable = false, length = 320)
    private String senderEmail;

    @Column(name = "first_observed_at", nullable = false)
    private Instant firstObservedAt;

    @Column(name = "last_observed_at", nullable = false)
    private Instant lastObservedAt;

    @Column(name = "observation_count", nullable = false)
    private int observationCount;

    @Column(name = "pattern_kind", nullable = false, length = 8)
    private String patternKind = PatternKind.EMAIL.id();

    @Column(name = "created_by_user", nullable = false)
    private boolean createdByUser;

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
        this.patternKind = PatternKind.EMAIL.id();
        this.createdByUser = false;
    }

    public TenantProtectedSenderObservationEntity(
            UUID id,
            UUID tenantId,
            String senderEmail,
            Instant observedAt,
            PatternKind patternKind,
            boolean createdByUser) {
        this(id, tenantId, senderEmail, observedAt);
        this.patternKind = requirePatternKind(patternKind).id();
        this.createdByUser = createdByUser;
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

    public String getPatternKindId() {
        return patternKind;
    }

    public PatternKind getPatternKind() {
        return PatternKind.fromId(patternKind);
    }

    public boolean isCreatedByUser() {
        return createdByUser;
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

    private static PatternKind requirePatternKind(PatternKind patternKind) {
        if (patternKind == null) {
            throw new IllegalArgumentException("patternKind must not be null");
        }
        return patternKind;
    }

    public enum PatternKind {
        EMAIL,
        DOMAIN;

        public String id() {
            return name();
        }

        public static PatternKind fromId(String id) {
            return Stream.of(values())
                    .filter(patternKind -> patternKind.id().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchElementException("Unknown PatternKind id: " + id));
        }
    }
}
