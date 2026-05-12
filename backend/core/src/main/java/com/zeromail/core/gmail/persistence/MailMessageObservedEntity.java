package com.zeromail.core.gmail.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "mail_message_observed")
@IdClass(MailMessageObservedId.class)
public class MailMessageObservedEntity {

    @Id
    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Column(name = "gmail_message_id", nullable = false)
    private String gmailMessageId;

    @Column(name = "gmail_thread_id", nullable = false)
    private String gmailThreadId;

    @Column(name = "history_id", nullable = false)
    private Long historyId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "label_ids", columnDefinition = "text[]", nullable = false)
    private String[] labelIds;

    @Column(name = "internal_date")
    private Long internalDate;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt = Instant.now();

    protected MailMessageObservedEntity() {}

    public MailMessageObservedEntity(
            UUID tenantId,
            String gmailMessageId,
            String gmailThreadId,
            Long historyId,
            String[] labelIds,
            Long internalDate) {
        this(
                tenantId,
                gmailMessageId,
                gmailThreadId,
                historyId,
                labelIds,
                internalDate,
                Instant.now());
    }

    public MailMessageObservedEntity(
            UUID tenantId,
            String gmailMessageId,
            String gmailThreadId,
            Long historyId,
            String[] labelIds,
            Long internalDate,
            Instant observedAt) {
        this.tenantId = tenantId;
        this.gmailMessageId = gmailMessageId;
        this.gmailThreadId = gmailThreadId;
        this.historyId = historyId;
        this.labelIds = labelIds;
        this.internalDate = internalDate;
        this.observedAt = observedAt;
    }

    public MailMessageObservedId getId() {
        return new MailMessageObservedId(tenantId, gmailMessageId);
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getGmailMessageId() {
        return gmailMessageId;
    }

    public String getGmailThreadId() {
        return gmailThreadId;
    }

    public Long getHistoryId() {
        return historyId;
    }

    public String[] getLabelIds() {
        return labelIds;
    }

    public Long getInternalDate() {
        return internalDate;
    }

    public Instant getObservedAt() {
        return observedAt;
    }
}
