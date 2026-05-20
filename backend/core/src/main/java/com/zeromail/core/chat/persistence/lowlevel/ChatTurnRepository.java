package com.zeromail.core.chat.persistence.lowlevel;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC writes against {@code chat} (header upsert + touch) and {@code assistant_pending_action}
 * (insert on tool-call) for the chat-turn use case. Per CONVENTIONS Section 1, the use-case
 * orchestrator must not embed SQL.
 */
@Repository
public class ChatTurnRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChatTurnRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    public void insertChatIfAbsent(UUID chatId, UUID tenantId, String title, Instant now) {
        Timestamp nowTimestamp = Timestamp.from(now);
        jdbcTemplate.update(
                """
                INSERT INTO chat (id, tenant_id, title, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 0)
                ON CONFLICT (id) DO NOTHING
                """,
                chatId,
                tenantId,
                title,
                nowTimestamp,
                nowTimestamp);
    }

    public void touchChatUpdatedAt(UUID chatId, UUID tenantId, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE chat
                SET updated_at = ?
                WHERE id = ? AND tenant_id = ?
                """,
                Timestamp.from(now),
                chatId,
                tenantId);
    }

    public void insertPendingAction(
            UUID chatId,
            UUID tenantId,
            UUID chatMessageId,
            String toolCallId,
            String draftBody,
            Instant expiresAt,
            Instant now) {
        Timestamp nowTimestamp = Timestamp.from(now);
        jdbcTemplate.update(
                """
                INSERT INTO assistant_pending_action (
                    id,
                    chat_id,
                    tenant_id,
                    chat_message_id,
                    tool_call_id,
                    state,
                    draft_body,
                    expires_at,
                    last_tool_call_id,
                    last_tool_call_state,
                    created_at,
                    updated_at,
                    version
                )
                VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, 'input-available', ?, ?, 0)
                """,
                UUID.randomUUID(),
                chatId,
                tenantId,
                chatMessageId,
                toolCallId,
                draftBody,
                Timestamp.from(expiresAt),
                toolCallId,
                nowTimestamp,
                nowTimestamp);
    }
}
