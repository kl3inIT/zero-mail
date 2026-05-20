package com.zeromail.core.chat.persistence.lowlevel;

import com.zeromail.core.chat.projection.ChatHistoryProjection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC reads + soft-delete write against {@code chat} (and joined {@code chat_message} for message
 * counts) for the chat-history use case. Per CONVENTIONS Section 1, the use-case service and
 * projector must not embed SQL.
 */
@Repository
public class ChatHistoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChatHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    public List<ChatHistoryProjection> listForTenant(UUID tenantId, int pageSize, int pageOffset) {
        return jdbcTemplate.query(
                """
                SELECT chat.id,
                       chat.title,
                       chat.updated_at,
                       COUNT(message.id)::int AS message_count
                FROM chat chat
                LEFT JOIN chat_message message
                       ON message.chat_id = chat.id
                      AND message.tenant_id = chat.tenant_id
                WHERE chat.tenant_id = ? AND chat.soft_deleted_at IS NULL
                GROUP BY chat.id, chat.title, chat.updated_at
                ORDER BY chat.updated_at DESC, chat.id DESC
                LIMIT ? OFFSET ?
                """,
                (resultSet, _) ->
                        new ChatHistoryProjection(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getString("title"),
                                toInstant(resultSet.getTimestamp("updated_at")),
                                resultSet.getInt("message_count")),
                tenantId,
                pageSize,
                pageOffset);
    }

    public Optional<ChatHeader> findChatHeader(UUID tenantId, UUID chatId) {
        return jdbcTemplate
                .query(
                        """
                        SELECT id, title, created_at, updated_at
                        FROM chat
                        WHERE id = ? AND tenant_id = ? AND soft_deleted_at IS NULL
                        """,
                        (resultSet, _) ->
                                new ChatHeader(
                                        resultSet.getObject("id", UUID.class),
                                        resultSet.getString("title"),
                                        toInstant(resultSet.getTimestamp("created_at")),
                                        toInstant(resultSet.getTimestamp("updated_at"))),
                        chatId,
                        tenantId)
                .stream()
                .findFirst();
    }

    public int softDeleteChat(UUID tenantId, UUID chatId) {
        Timestamp now = Timestamp.from(Instant.now());
        return jdbcTemplate.update(
                """
                UPDATE chat
                SET soft_deleted_at = ?, updated_at = ?
                WHERE id = ? AND tenant_id = ? AND soft_deleted_at IS NULL
                """,
                now,
                now,
                chatId,
                tenantId);
    }

    public boolean chatExists(UUID tenantId, UUID chatId) {
        Boolean exists =
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
        return Boolean.TRUE.equals(exists);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }

    public record ChatHeader(UUID id, String title, Instant createdAt, Instant updatedAt) {}
}
