package com.zeromail.core.messaging.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.messaging.domain.MessagingChannel;
import com.zeromail.core.support.PostgresContainerTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressWarnings("SqlResolve")
class MessagingConversationResolverTest extends PostgresContainerTest {

    private static final String TELEGRAM_BOT_ACCOUNT = "zeromail-telegram-bot";
    private static final String TELEGRAM_CHAT_ID = "5378705410";
    private static final String TELEGRAM_USER_ID = "5378705410";

    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired MessagingConversationResolver messagingConversationResolver;

    @Test
    void resolveOrCreateActiveSession_reusesStableChatForSameExternalConversation() {
        UUID tenantId = seedTenant();
        MessagingConversationResolveCommand command = telegramCommand(tenantId, null);

        MessagingConversationResolution firstResolution =
                messagingConversationResolver.resolveOrCreateActiveSession(command);
        MessagingConversationResolution secondResolution =
                messagingConversationResolver.resolveOrCreateActiveSession(command);

        assertThat(secondResolution.chatId()).isEqualTo(firstResolution.chatId());
        assertThat(secondResolution.conversationId()).isEqualTo(firstResolution.conversationId());
        assertThat(secondResolution.conversationSessionId())
                .isEqualTo(firstResolution.conversationSessionId());
        assertThat(firstResolution.createdConversation()).isTrue();
        assertThat(firstResolution.createdSession()).isTrue();
        assertThat(secondResolution.createdConversation()).isFalse();
        assertThat(secondResolution.createdSession()).isFalse();
        assertThat(countTelegramSessions(tenantId)).isEqualTo(1);
    }

    @Test
    void startNewSession_changesTheActiveChatWithoutLosingConversationBinding() {
        UUID tenantId = seedTenant();
        MessagingConversationResolveCommand command = telegramCommand(tenantId, null);
        MessagingConversationResolution firstResolution =
                messagingConversationResolver.resolveOrCreateActiveSession(command);

        MessagingConversationResolution newSessionResolution =
                messagingConversationResolver.startNewSession(command);
        MessagingConversationResolution activeResolution =
                messagingConversationResolver.resolveOrCreateActiveSession(command);

        assertThat(newSessionResolution.conversationId())
                .isEqualTo(firstResolution.conversationId());
        assertThat(newSessionResolution.chatId()).isNotEqualTo(firstResolution.chatId());
        assertThat(activeResolution.chatId()).isEqualTo(newSessionResolution.chatId());
        assertThat(activeResolution.conversationSessionId())
                .isEqualTo(newSessionResolution.conversationSessionId());
        assertThat(countTelegramSessions(tenantId)).isEqualTo(2);
    }

    @Test
    void resolveOrCreateActiveSession_separatesDefaultMailboxFromSpecificMailAccount() {
        UUID tenantId = seedTenant();
        UUID gmailConnectionId = seedGmailConnection(tenantId);

        MessagingConversationResolution defaultMailboxResolution =
                messagingConversationResolver.resolveOrCreateActiveSession(
                        telegramCommand(tenantId, null));
        MessagingConversationResolution gmailScopedResolution =
                messagingConversationResolver.resolveOrCreateActiveSession(
                        telegramCommand(tenantId, gmailConnectionId));

        assertThat(gmailScopedResolution.chatId()).isNotEqualTo(defaultMailboxResolution.chatId());
        assertThat(gmailScopedResolution.conversationId())
                .isNotEqualTo(defaultMailboxResolution.conversationId());
        assertThat(countTelegramConversations(tenantId)).isEqualTo(2);
    }

    private MessagingConversationResolveCommand telegramCommand(UUID tenantId, UUID mailAccountId) {
        return new MessagingConversationResolveCommand(
                tenantId,
                MessagingChannel.TELEGRAM,
                TELEGRAM_BOT_ACCOUNT,
                TELEGRAM_CHAT_ID,
                TELEGRAM_USER_ID,
                mailAccountId,
                "Telegram");
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants (id, display_name, created_at) VALUES (?, ?, now())",
                tenantId,
                "Messaging Tenant");
        return tenantId;
    }

    private UUID seedGmailConnection(UUID tenantId) {
        UUID gmailConnectionId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO gmail_connections (
                    id,
                    tenant_id,
                    google_email,
                    status,
                    connected_at,
                    created_at,
                    updated_at,
                    watch_consecutive_failures,
                    ingestion_health,
                    version
                )
                VALUES (?, ?, ?, 'CONNECTED', now(), now(), now(), 0, 'HEALTHY', 0)
                """,
                gmailConnectionId,
                tenantId,
                "owner@example.test");
        return gmailConnectionId;
    }

    private Integer countTelegramSessions(UUID tenantId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM messaging_conversation_session
                WHERE tenant_id = ?
                """,
                Integer.class,
                tenantId);
    }

    private Integer countTelegramConversations(UUID tenantId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM messaging_conversation
                WHERE tenant_id = ?
                """,
                Integer.class,
                tenantId);
    }
}
