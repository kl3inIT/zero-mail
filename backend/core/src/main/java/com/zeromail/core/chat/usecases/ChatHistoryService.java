package com.zeromail.core.chat.usecases;

import com.zeromail.core.chat.exception.ChatNotFoundException;
import com.zeromail.core.chat.projection.ChatHistoryDetail;
import com.zeromail.core.chat.projection.ChatHistoryProjection;
import com.zeromail.core.chat.projection.ChatHistoryProjector;
import com.zeromail.core.tenant.TenantContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@SuppressWarnings("SqlResolve")
public class ChatHistoryService {

    private final ChatHistoryProjector chatHistoryProjector;
    private final ZeroMailChatProperties chatProperties;
    private final JdbcTemplate jdbcTemplate;

    public ChatHistoryService(
            ChatHistoryProjector chatHistoryProjector,
            ZeroMailChatProperties chatProperties,
            JdbcTemplate jdbcTemplate) {
        this.chatHistoryProjector = chatHistoryProjector;
        this.chatProperties = chatProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<ChatHistoryProjection> listForCurrentTenant(Integer pageSize, Integer pageOffset) {
        int effectivePageSize =
                pageSize == null || pageSize < 1 ? chatProperties.history().pageSize() : pageSize;
        int effectivePageOffset = pageOffset == null || pageOffset < 0 ? 0 : pageOffset;
        return chatHistoryProjector.listForTenant(
                TenantContext.currentTenantUuid(), effectivePageSize, effectivePageOffset);
    }

    @Transactional(readOnly = true)
    public ChatHistoryDetail loadConversation(UUID chatId) {
        ChatHistoryDetail detail =
                chatHistoryProjector.project(TenantContext.currentTenantUuid(), chatId);
        if (detail == null) {
            throw new ChatNotFoundException(chatId);
        }
        return detail;
    }

    @Transactional
    public void softDelete(UUID chatId) {
        UUID tenantId = TenantContext.currentTenantUuid();
        int updatedRows =
                jdbcTemplate.update(
                        """
                        UPDATE chat
                        SET soft_deleted_at = ?, updated_at = ?
                        WHERE id = ? AND tenant_id = ? AND soft_deleted_at IS NULL
                        """,
                        Timestamp.from(Instant.now()),
                        Timestamp.from(Instant.now()),
                        chatId,
                        tenantId);
        if (updatedRows == 1) {
            return;
        }
        // updatedRows == 0: either the chat does not exist for this tenant, or it is already
        // soft-deleted. Distinguish the two cases so a retry on a transient network failure
        // does not surface as 404 -- soft delete is idempotent by definition (WR-06).
        Boolean chatExists =
                jdbcTemplate.queryForObject(
                        """
                        SELECT EXISTS (
                            SELECT 1
                              FROM chat
                             WHERE id = ?
                               AND tenant_id = ?
                        )
                        """,
                        Boolean.class,
                        chatId,
                        tenantId);
        if (!Boolean.TRUE.equals(chatExists)) {
            throw new ChatNotFoundException(chatId);
        }
        // Chat exists but was already soft-deleted; treat as idempotent success (204 No Content).
    }
}
