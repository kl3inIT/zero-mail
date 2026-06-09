package com.zeromail.core.support.persistence;

import com.zeromail.core.support.domain.FeedbackStatus;
import com.zeromail.core.support.domain.FeedbackType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "feedback_submission")
@EntityListeners(AuditingEntityListener.class)
public class FeedbackSubmissionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Null for unauthenticated landing page submissions. */
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private FeedbackType type;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "contact_email", nullable = false, length = 320)
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FeedbackStatus status = FeedbackStatus.OPEN;

    @Column(name = "admin_notes", columnDefinition = "text")
    private String adminNotes;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @jakarta.persistence.Version
    @Column(name = "version", nullable = false)
    private Integer version;

    protected FeedbackSubmissionEntity() {}

    public FeedbackSubmissionEntity(
            UUID id,
            UUID tenantId,
            FeedbackType type,
            String subject,
            String message,
            String contactEmail) {
        this.id = id;
        this.tenantId = tenantId;
        this.type = type;
        this.subject = subject;
        this.message = message;
        this.contactEmail = contactEmail;
        this.status = FeedbackStatus.OPEN;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public FeedbackType getType() {
        return type;
    }

    public String getSubject() {
        return subject;
    }

    public String getMessage() {
        return message;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public FeedbackStatus getStatus() {
        return status;
    }

    public String getAdminNotes() {
        return adminNotes;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void resolve(String adminNotes, Instant resolvedAt) {
        this.status = FeedbackStatus.RESOLVED;
        this.adminNotes = adminNotes;
        this.resolvedAt = resolvedAt;
    }

    public void reopen() {
        this.status = FeedbackStatus.OPEN;
        this.resolvedAt = null;
    }
}
