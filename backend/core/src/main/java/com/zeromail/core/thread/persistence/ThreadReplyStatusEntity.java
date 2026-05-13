package com.zeromail.core.thread.persistence;

import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import com.zeromail.core.thread.domain.ThreadReplyBucket;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "thread_reply_status")
@SuppressWarnings("JpaDataSourceORMInspection")
public class ThreadReplyStatusEntity extends AbstractTenantOwnedEntity {

    @Column(name = "gmail_thread_id", nullable = false, length = 255)
    private String gmailThreadId;

    @Convert(converter = ThreadReplyBucketAttributeConverter.class)
    @Column(name = "bucket", nullable = false, length = 32)
    private ThreadReplyBucket bucket;

    @Column(name = "last_classified_message_id", length = 255)
    private String lastClassifiedMessageId;

    @Column(name = "last_classified_at")
    private Instant lastClassifiedAt;

    @Column(name = "has_draft", nullable = false)
    private boolean hasDraft;

    @Column(name = "draft_id", length = 255)
    private String draftId;

    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    protected ThreadReplyStatusEntity() {
        // Hibernate
    }

    public ThreadReplyStatusEntity(
            UUID id,
            UUID tenantId,
            String gmailThreadId,
            ThreadReplyBucket bucket,
            String lastClassifiedMessageId,
            Instant lastClassifiedAt,
            boolean hasDraft,
            String draftId,
            boolean resolved) {
        super(id, tenantId);
        this.gmailThreadId = requireText(gmailThreadId, "gmailThreadId");
        this.bucket = Objects.requireNonNull(bucket, "bucket must not be null");
        this.lastClassifiedMessageId = lastClassifiedMessageId;
        this.lastClassifiedAt = lastClassifiedAt;
        this.hasDraft = hasDraft;
        this.draftId = nullIfBlank(draftId);
        this.resolved = resolved;
    }

    public String getGmailThreadId() {
        return gmailThreadId;
    }

    public ThreadReplyBucket getBucket() {
        return bucket;
    }

    public void setBucket(ThreadReplyBucket bucket) {
        this.bucket = Objects.requireNonNull(bucket, "bucket must not be null");
    }

    public String getLastClassifiedMessageId() {
        return lastClassifiedMessageId;
    }

    public void setLastClassifiedMessageId(String lastClassifiedMessageId) {
        this.lastClassifiedMessageId =
                requireText(lastClassifiedMessageId, "lastClassifiedMessageId");
    }

    public Instant getLastClassifiedAt() {
        return lastClassifiedAt;
    }

    public void setLastClassifiedAt(Instant lastClassifiedAt) {
        this.lastClassifiedAt =
                Objects.requireNonNull(lastClassifiedAt, "lastClassifiedAt must not be null");
    }

    public boolean hasDraft() {
        return hasDraft;
    }

    public void setHasDraft(boolean hasDraft) {
        this.hasDraft = hasDraft;
    }

    public String getDraftId() {
        return draftId;
    }

    public void setDraftId(String draftId) {
        this.draftId = nullIfBlank(draftId);
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmedValue = value.trim();
        if (trimmedValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmedValue;
    }

    private static String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
