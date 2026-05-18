package com.zeromail.core.chat.persistence;

import com.zeromail.core.chat.domain.ChatMessage;
import com.zeromail.core.chat.domain.parts.ChatMessageParts;
import com.zeromail.core.chat.exception.BodyContentBanViolationException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("SqlResolve")
public class ChatMessageJdbcRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ChatPartsJsonConverter chatPartsJsonConverter;
    private final ChatMessageRowMapper chatMessageRowMapper;

    public ChatMessageJdbcRepository(
            JdbcTemplate jdbcTemplate, ChatPartsJsonConverter chatPartsJsonConverter) {
        this.jdbcTemplate = jdbcTemplate;
        this.chatPartsJsonConverter = chatPartsJsonConverter;
        this.chatMessageRowMapper = new ChatMessageRowMapper(chatPartsJsonConverter);
    }

    public UUID insert(ChatMessage chatMessage) {
        UUID messageId = chatMessage.id() == null ? UUID.randomUUID() : chatMessage.id().value();
        Instant createdAt =
                chatMessage.createdAt() == null ? Instant.now() : chatMessage.createdAt();
        ChatMessageParts parts =
                chatMessage.parts() == null
                        ? ChatMessageParts.v1(java.util.List.of())
                        : chatMessage.parts();
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO chat_message (id, chat_id, tenant_id, role, parts, created_at)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?)
                    """,
                    messageId,
                    chatMessage.chatId().value(),
                    UUID.fromString(chatMessage.tenantId()),
                    chatMessage.role().id(),
                    chatPartsJsonConverter.toJson(parts),
                    Timestamp.from(createdAt));
        } catch (DataAccessException dataAccessException) {
            if (isBodyBanViolation(dataAccessException)) {
                throw new BodyContentBanViolationException(dataAccessException);
            }
            throw dataAccessException;
        }
        return messageId;
    }

    public Optional<ChatMessage> findById(UUID messageId) {
        return jdbcTemplate
                .query(
                        """
                        SELECT id, chat_id, tenant_id, role, parts::text AS parts, created_at
                        FROM chat_message
                        WHERE id = ?
                        """,
                        chatMessageRowMapper,
                        messageId)
                .stream()
                .findFirst();
    }

    private static boolean isBodyBanViolation(DataAccessException dataAccessException) {
        Throwable currentThrowable = dataAccessException;
        while (currentThrowable != null) {
            String message = currentThrowable.getMessage();
            if (message != null && message.contains("Chat persistence violation")) {
                return true;
            }
            currentThrowable = currentThrowable.getCause();
        }
        return false;
    }
}
