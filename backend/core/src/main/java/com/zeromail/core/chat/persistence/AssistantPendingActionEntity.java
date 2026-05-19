package com.zeromail.core.chat.persistence;

import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assistant_pending_action")
@SuppressWarnings({"JpaDataSourceORMInspection", "unused"})
public class AssistantPendingActionEntity extends AbstractTenantOwnedEntity {

    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @Column(name = "chat_message_id", nullable = false)
    private UUID chatMessageId;

    @Column(name = "tool_call_id", nullable = false, length = 64)
    private String toolCallId;

    @Column(name = "state", nullable = false, length = 16)
    private String state;

    @Column(name = "parts_updated_at", nullable = false)
    private Instant partsUpdatedAt;

    @Column(name = "redis_lease_key", length = 128)
    private String redisLeaseKey;

    @Column(name = "draft_body")
    private String draftBody;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_tool_call_id", length = 64)
    private String lastToolCallId;

    @Column(name = "last_tool_call_state", length = 16)
    private String lastToolCallState;

    protected AssistantPendingActionEntity() {
        // Hibernate
    }

    public AssistantPendingActionEntity(
            UUID pendingActionId,
            UUID tenantId,
            UUID chatId,
            UUID chatMessageId,
            String toolCallId,
            String state,
            Instant expiresAt,
            String draftBody) {
        super(pendingActionId, tenantId);
        this.chatId = chatId;
        this.chatMessageId = chatMessageId;
        this.toolCallId = toolCallId;
        this.state = state;
        this.expiresAt = expiresAt;
        this.draftBody = draftBody;
        this.partsUpdatedAt = Instant.now();
    }

    public UUID getPendingActionId() {
        return getId();
    }

    public UUID getChatId() {
        return chatId;
    }

    public UUID getChatMessageId() {
        return chatMessageId;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getState() {
        return state;
    }

    public Instant getPartsUpdatedAt() {
        return partsUpdatedAt;
    }

    public String getDraftBody() {
        return draftBody;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
