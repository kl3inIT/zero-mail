package com.zeromail.core.chat.projection;

import com.zeromail.core.chat.domain.ChatMessage;
import com.zeromail.core.chat.persistence.ChatMessageJdbcRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@SuppressWarnings("SqlResolve")
public class ChatHistoryProjector {

    private final JdbcTemplate jdbcTemplate;
    private final ChatMessageJdbcRepository chatMessageRepository;

    public ChatHistoryProjector(
            JdbcTemplate jdbcTemplate, ChatMessageJdbcRepository chatMessageRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.chatMessageRepository = chatMessageRepository;
    }

    public List<ChatHistoryProjection> listForTenant(UUID tenantId, int pageSize, int pageOffset) {
        return jdbcTemplate.query(
                """
                SELECT c.id,
                       c.title,
                       c.updated_at,
                       COUNT(m.id)::int AS message_count
                FROM chat c
                LEFT JOIN chat_message m ON m.chat_id = c.id AND m.tenant_id = c.tenant_id
                WHERE c.tenant_id = ? AND c.soft_deleted_at IS NULL
                GROUP BY c.id, c.title, c.updated_at
                ORDER BY c.updated_at DESC, c.id DESC
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

    public ChatHistoryDetail project(UUID tenantId, UUID chatId) {
        ChatHeader header =
                jdbcTemplate
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
                        .findFirst()
                        .orElse(null);
        if (header == null) {
            return null;
        }
        List<ChatMessageProjection> messages =
                chatMessageRepository.findByChatIdOrderByCreatedAtAsc(chatId).stream()
                        .filter(chatMessage -> chatMessage.tenantId().equals(tenantId.toString()))
                        .map(ChatHistoryProjector::toProjection)
                        .toList();
        return new ChatHistoryDetail(
                header.id(), header.title(), header.createdAt(), header.updatedAt(), messages);
    }

    private static ChatMessageProjection toProjection(ChatMessage chatMessage) {
        return new ChatMessageProjection(
                chatMessage.id().value(),
                chatMessage.role(),
                chatMessage.parts(),
                chatMessage.createdAt());
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }

    private record ChatHeader(UUID id, String title, Instant createdAt, Instant updatedAt) {}
}
