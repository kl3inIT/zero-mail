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
                        TenantContext.currentTenantUuid());
        if (updatedRows == 0) {
            throw new ChatNotFoundException(chatId);
        }
    }
}
