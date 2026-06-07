package com.zeromail.core.messaging.persistence;

import com.zeromail.core.messaging.domain.MessagingChannel;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MessagingConversationJdbcRepository {

    private static final UUID DEFAULT_MAIL_ACCOUNT_SCOPE =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final JdbcTemplate jdbcTemplate;

    public MessagingConversationJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    public UUID upsertChannelAccount(
            UUID tenantId,
            MessagingChannel channel,
            String externalAccountId,
            String displayName,
            Instant now) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO messaging_channel_account (
                    id,
                    tenant_id,
                    channel,
                    external_account_id,
                    display_name,
                    status,
                    linked_at,
                    last_active_at,
                    created_at,
                    updated_at,
                    version
                )
                VALUES (?, ?, ?, ?, ?, 'CONNECTED', ?, ?, ?, ?, 0)
                ON CONFLICT (tenant_id, channel, external_account_id)
                DO UPDATE SET
                    display_name = COALESCE(EXCLUDED.display_name, messaging_channel_account.display_name),
                    last_active_at = EXCLUDED.last_active_at,
                    updated_at = EXCLUDED.updated_at,
                    status = CASE
                        WHEN messaging_channel_account.status = 'DISCONNECTED' THEN 'CONNECTED'
                        ELSE messaging_channel_account.status
                    END
                RETURNING id
                """,
                UUID.class,
                UUID.randomUUID(),
                tenantId,
                channel.name(),
                externalAccountId,
                displayName,
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now));
    }

    public UpsertedConversation upsertConversation(
            UUID tenantId,
            UUID channelAccountId,
            UUID mailAccountId,
            String externalConversationId,
            String externalUserId,
            Instant now) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO messaging_conversation (
                    id,
                    tenant_id,
                    channel_account_id,
                    mail_account_id,
                    external_conversation_id,
                    external_user_id,
                    status,
                    last_active_at,
                    created_at,
                    updated_at,
                    version
                )
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, 0)
                ON CONFLICT (
                    tenant_id,
                    channel_account_id,
                    external_conversation_id,
                    COALESCE(mail_account_id, '00000000-0000-0000-0000-000000000000'::uuid)
                )
                DO UPDATE SET
                    external_user_id = COALESCE(EXCLUDED.external_user_id, messaging_conversation.external_user_id),
                    last_active_at = EXCLUDED.last_active_at,
                    updated_at = EXCLUDED.updated_at,
                    status = CASE
                        WHEN messaging_conversation.status = 'DISCONNECTED' THEN 'ACTIVE'
                        ELSE messaging_conversation.status
                    END
                RETURNING id, active_chat_id, (xmax = 0) AS inserted
                """,
                (resultSet, rowNumber) ->
                        new UpsertedConversation(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getObject("active_chat_id", UUID.class),
                                resultSet.getBoolean("inserted")),
                UUID.randomUUID(),
                tenantId,
                channelAccountId,
                mailAccountId,
                externalConversationId,
                externalUserId,
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now));
    }

    public ConversationLock lockConversation(UUID tenantId, UUID conversationId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT id, active_chat_id
                FROM messaging_conversation
                WHERE tenant_id = ? AND id = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) ->
                        new ConversationLock(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getObject("active_chat_id", UUID.class)),
                tenantId,
                conversationId);
    }

    public void setActiveChat(UUID tenantId, UUID conversationId, UUID chatId, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE messaging_conversation
                SET active_chat_id = ?, last_active_at = ?, updated_at = ?
                WHERE tenant_id = ? AND id = ?
                """,
                chatId,
                Timestamp.from(now),
                Timestamp.from(now),
                tenantId,
                conversationId);
    }

    public UUID insertConversationSession(
            UUID tenantId, UUID conversationId, UUID chatId, String title, Instant now) {
        UUID conversationSessionId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO messaging_conversation_session (
                    id,
                    tenant_id,
                    conversation_id,
                    chat_id,
                    title,
                    status,
                    last_used_at,
                    created_at,
                    updated_at,
                    version
                )
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, 0)
                """,
                conversationSessionId,
                tenantId,
                conversationId,
                chatId,
                title,
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now));
        return conversationSessionId;
    }

    public void insertChatIfAbsent(UUID tenantId, UUID chatId, String title, Instant now) {
        jdbcTemplate.update(
                """
                INSERT INTO chat (id, tenant_id, title, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 0)
                ON CONFLICT (id) DO NOTHING
                """,
                chatId,
                tenantId,
                title,
                Timestamp.from(now),
                Timestamp.from(now));
    }

    public Optional<ConversationSession> findSessionByChatId(
            UUID tenantId, UUID conversationId, UUID chatId) {
        return jdbcTemplate
                .query(
                        """
                        SELECT id, chat_id
                        FROM messaging_conversation_session
                        WHERE tenant_id = ? AND conversation_id = ? AND chat_id = ?
                        """,
                        (resultSet, rowNumber) -> mapConversationSession(resultSet),
                        tenantId,
                        conversationId,
                        chatId)
                .stream()
                .findFirst();
    }

    public void touchSession(UUID tenantId, UUID conversationSessionId, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE messaging_conversation_session
                SET last_used_at = ?, updated_at = ?
                WHERE tenant_id = ? AND id = ?
                """,
                Timestamp.from(now),
                Timestamp.from(now),
                tenantId,
                conversationSessionId);
    }

    public static UUID defaultMailAccountScope() {
        return DEFAULT_MAIL_ACCOUNT_SCOPE;
    }

    private static ConversationSession mapConversationSession(ResultSet resultSet)
            throws SQLException {
        return new ConversationSession(
                resultSet.getObject("id", UUID.class), resultSet.getObject("chat_id", UUID.class));
    }

    public record UpsertedConversation(UUID conversationId, UUID activeChatId, boolean inserted) {}

    public record ConversationLock(UUID conversationId, UUID activeChatId) {}

    public record ConversationSession(UUID conversationSessionId, UUID chatId) {}
}
