package com.zeromail.core.messaging.telegram.persistence;

import com.zeromail.core.messaging.telegram.domain.TelegramAccountStatus;
import com.zeromail.core.messaging.telegram.domain.TelegramAccountView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TelegramAccountJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public TelegramAccountJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsertConnectedAccount(
            UUID tenantId,
            long telegramChatId,
            long telegramUserId,
            String telegramUsername,
            String languageCode,
            Instant now) {
        jdbcTemplate.update(
                """
                DELETE FROM telegram_account
                WHERE tenant_id = ? OR telegram_chat_id = ?
                """,
                tenantId,
                telegramChatId);
        jdbcTemplate.update(
                """
                INSERT INTO telegram_account (
                    id,
                    tenant_id,
                    telegram_chat_id,
                    telegram_user_id,
                    telegram_username,
                    language_code,
                    status,
                    notifications_enabled,
                    notification_filter,
                    linked_at,
                    last_active_at,
                    created_at,
                    updated_at,
                    version
                )
                VALUES (?, ?, ?, ?, ?, ?, 'CONNECTED', true, '{}'::jsonb, ?, ?, ?, ?, 0)
                """,
                UUID.randomUUID(),
                tenantId,
                telegramChatId,
                telegramUserId,
                blankToNull(telegramUsername),
                blankToNull(languageCode),
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now));
    }

    public Optional<TelegramAccountView> findByTenantId(UUID tenantId) {
        return jdbcTemplate
                .query(
                        """
                        SELECT tenant_id, telegram_chat_id, telegram_user_id, telegram_username,
                               language_code, status, linked_at, last_active_at
                        FROM telegram_account
                        WHERE tenant_id = ?
                        """,
                        (resultSet, rowNumber) -> map(resultSet),
                        tenantId)
                .stream()
                .findFirst();
    }

    public Optional<TelegramAccountView> findByTelegramChatId(long telegramChatId) {
        return jdbcTemplate
                .query(
                        """
                        SELECT tenant_id, telegram_chat_id, telegram_user_id, telegram_username,
                               language_code, status, linked_at, last_active_at
                        FROM telegram_account
                        WHERE telegram_chat_id = ?
                        """,
                        (resultSet, rowNumber) -> map(resultSet),
                        telegramChatId)
                .stream()
                .findFirst();
    }

    public void markDisconnected(UUID tenantId, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE telegram_account
                SET status = 'DISCONNECTED', disconnected_at = ?, updated_at = ?
                WHERE tenant_id = ?
                """,
                Timestamp.from(now),
                Timestamp.from(now),
                tenantId);
    }

    public void touchLastActive(UUID tenantId, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE telegram_account
                SET last_active_at = ?, updated_at = ?
                WHERE tenant_id = ?
                """,
                Timestamp.from(now),
                Timestamp.from(now),
                tenantId);
    }

    private static TelegramAccountView map(ResultSet resultSet) throws SQLException {
        return new TelegramAccountView(
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getLong("telegram_chat_id"),
                resultSet.getLong("telegram_user_id"),
                resultSet.getString("telegram_username"),
                resultSet.getString("language_code"),
                TelegramAccountStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("linked_at").toInstant(),
                timestampToInstant(resultSet.getTimestamp("last_active_at")));
    }

    private static Instant timestampToInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
