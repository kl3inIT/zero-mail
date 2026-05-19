package com.zeromail.core.chat.persistence;

import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "assistant_action_audit")
@SuppressWarnings({"JpaDataSourceORMInspection", "unused"})
public class AssistantActionAuditEntity extends AbstractTenantOwnedEntity {

    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @Column(name = "tool_call_id", nullable = false, length = 64)
    private String toolCallId;

    @Column(name = "tool_category", nullable = false, length = 32)
    private String toolCategory;

    @Column(name = "tool_name", nullable = false, length = 64)
    private String toolName;

    @Column(name = "state", nullable = false, length = 16)
    private String state;

    @Column(name = "recipient_hash", length = 64)
    private String recipientHash;

    @Column(name = "subject_hash", length = 64)
    private String subjectHash;

    @Column(name = "gmail_message_id", length = 128)
    private String gmailMessageId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_summary_json", columnDefinition = "jsonb", nullable = false)
    private String resultSummaryJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preview_snapshot", columnDefinition = "jsonb", nullable = false)
    private String previewSnapshotJson;

    @Column(name = "in_flight_at")
    private Instant inFlightAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected AssistantActionAuditEntity() {
        // Hibernate
    }

    public AssistantActionAuditEntity(
            UUID auditId,
            UUID tenantId,
            UUID chatId,
            String toolCallId,
            String toolCategory,
            String toolName,
            String state,
            String recipientHash,
            String subjectHash,
            String gmailMessageId,
            String resultSummaryJson,
            String previewSnapshotJson,
            Instant inFlightAt,
            Instant sentAt) {
        super(auditId, tenantId);
        this.chatId = chatId;
        this.toolCallId = toolCallId;
        this.toolCategory = toolCategory;
        this.toolName = toolName;
        this.state = state;
        this.recipientHash = recipientHash;
        this.subjectHash = subjectHash;
        this.gmailMessageId = gmailMessageId;
        this.resultSummaryJson = resultSummaryJson == null ? "{}" : resultSummaryJson;
        this.previewSnapshotJson = previewSnapshotJson == null ? "{}" : previewSnapshotJson;
        this.inFlightAt = inFlightAt;
        this.sentAt = sentAt;
    }

    public UUID getAuditId() {
        return getId();
    }

    public UUID getChatId() {
        return chatId;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getToolCategory() {
        return toolCategory;
    }

    public String getToolName() {
        return toolName;
    }

    public String getState() {
        return state;
    }

    public String getGmailMessageId() {
        return gmailMessageId;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
