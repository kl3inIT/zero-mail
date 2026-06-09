package com.zeromail.core.messaging.telegram.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.support.PostgresContainerTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
        properties = {
            "zero-mail.messaging.telegram.enabled=true",
            "zero-mail.messaging.telegram.bot-token=test-token",
            "zero-mail.messaging.telegram.bot-username=ZeroMailBot",
            "zero-mail.messaging.telegram.webhook-secret-token=test-webhook-secret",
            "zero-mail.messaging.telegram.messaging-link-secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        })
class PairingConsumeServiceTest extends PostgresContainerTest {

    private static final long TELEGRAM_CHAT_ID = 5378705410L;
    private static final long TELEGRAM_USER_ID = 5378705410L;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private PairingCodeService pairingCodeService;

    @Autowired private PairingConsumeService pairingConsumeService;

    @AfterEach
    void cleanTelegramRows() {
        jdbcTemplate.update(
                "DELETE FROM telegram_account WHERE telegram_chat_id = ?", TELEGRAM_CHAT_ID);
    }

    @Test
    void consume_relinksExistingTelegramChatToPairingTenant() {
        UUID oldTenantId = UUID.randomUUID();
        UUID newTenantId = UUID.randomUUID();
        insertTenant(oldTenantId, "Old tenant");
        insertTenant(newTenantId, "New tenant");
        insertConnectedTelegramAccount(oldTenantId);

        String pairingCode = pairingCodeService.mint(newTenantId).code();

        UUID consumedTenantId =
                pairingConsumeService.consume(
                        pairingCode, TELEGRAM_CHAT_ID, TELEGRAM_USER_ID, "nhuxuanviet", "vi");

        assertThat(consumedTenantId).isEqualTo(newTenantId);
        assertThat(telegramAccountCount()).isEqualTo(1);
        assertThat(tenantIdForTelegramChat()).isEqualTo(newTenantId);
    }

    private void insertTenant(UUID tenantId, String displayName) {
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)", tenantId, displayName);
    }

    private void insertConnectedTelegramAccount(UUID tenantId) {
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
                VALUES (gen_random_uuid(), ?, ?, ?, 'olduser', 'vi', 'CONNECTED', true,
                        '{}'::jsonb, now(), now(), now(), now(), 0)
                """,
                tenantId,
                TELEGRAM_CHAT_ID,
                TELEGRAM_USER_ID);
    }

    private Integer telegramAccountCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM telegram_account WHERE telegram_chat_id = ?",
                Integer.class,
                TELEGRAM_CHAT_ID);
    }

    private UUID tenantIdForTelegramChat() {
        return jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM telegram_account WHERE telegram_chat_id = ?",
                UUID.class,
                TELEGRAM_CHAT_ID);
    }
}
