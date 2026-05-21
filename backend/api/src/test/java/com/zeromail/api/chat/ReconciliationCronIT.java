package com.zeromail.api.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SuppressWarnings("SqlResolve")
class ReconciliationCronIT extends ApiPostgresTestBase {

    private static final String RECOVERED_GMAIL_MESSAGE_ID =
            "<test.tenant.chat1.toolA@zero-mail.invalid>";
    private static final String LOST_GMAIL_MESSAGE_ID =
            "<test.tenant.chat2.toolB@zero-mail.invalid>";
    private static final String RECENT_GMAIL_MESSAGE_ID =
            "<test.tenant.chat3.toolC@zero-mail.invalid>";

    @Autowired AssistantPendingActionReconciler reconciler;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MeterRegistry meterRegistry;

    @MockitoBean GmailApiClientFactory gmailApiClientFactory;

    private Logger reconcilerLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void cleanChatTablesAndAttachLogCapture() {
        jdbcTemplate.execute(
                """
                TRUNCATE TABLE
                  assistant_action_audit,
                  assistant_pending_action,
                  chat_message,
                  chat
                RESTART IDENTITY CASCADE
                """);
        reconcilerLogger = (Logger) LoggerFactory.getLogger(AssistantPendingActionReconciler.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        reconcilerLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogCapture() {
        reconcilerLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void reconciliation_cron_heals_expired_leases_and_stale_send_in_flight_audits()
            throws Exception {
        UUID tenantAId = seedTenant("reconcile-a");
        UUID tenantBId = seedTenant("reconcile-b");

        ExpiredLeaseSeeds expiredLeaseSeeds = seedExpiredLeaseCases(tenantAId);
        StaleSendSeeds staleSendSeeds = seedStaleSendCases(tenantAId, tenantBId);
        Gmail.Users.Messages.List tenantAListRequest =
                configureGmailLookup(tenantAId, RECOVERED_GMAIL_MESSAGE_ID, true);
        Gmail.Users.Messages.List tenantBListRequest =
                configureGmailLookup(tenantBId, LOST_GMAIL_MESSAGE_ID, false);

        double residualLeasesBefore = counterValue("chat_reconciliation_residual_leases_total");
        double auditMismatchBefore =
                counterValue("chat_reconciliation_audit_vs_state_mismatch_total");
        double recoveredBefore = counterValue("chat_reconciliation_send_recovered_total");
        double lostBefore = counterValue("chat_reconciliation_send_lost_total");

        reconciler.reconcileExpiredLeases();
        reconciler.reconcileStaleSendInFlight();

        assertPendingState(expiredLeaseSeeds.committedAuditPendingActionId(), "CONFIRMED");
        assertPendingState(expiredLeaseSeeds.missingAuditPendingActionId(), "FAILED");
        assertPendingState(expiredLeaseSeeds.notExpiredPendingActionId(), "PROCESSING");
        assertPendingState(expiredLeaseSeeds.alreadyConfirmedPendingActionId(), "CONFIRMED");

        assertAuditState(staleSendSeeds.recoveredAuditId(), "COMMITTED");
        assertAuditSentAtIsPresent(staleSendSeeds.recoveredAuditId());
        assertPendingState(staleSendSeeds.recoveredPendingActionId(), "CONFIRMED");
        assertAuditState(staleSendSeeds.lostAuditId(), "FAILED");
        assertAuditSentAtIsNull(staleSendSeeds.lostAuditId());
        assertPendingState(staleSendSeeds.lostPendingActionId(), "FAILED");
        assertAuditState(staleSendSeeds.recentAuditId(), "SEND_IN_FLIGHT");
        assertPendingState(staleSendSeeds.recentPendingActionId(), "PROCESSING");

        assertThat(counterValue("chat_reconciliation_residual_leases_total"))
                .isEqualTo(residualLeasesBefore + 2.0d);
        assertThat(counterValue("chat_reconciliation_audit_vs_state_mismatch_total"))
                .isEqualTo(auditMismatchBefore + 1.0d);
        assertThat(counterValue("chat_reconciliation_send_recovered_total"))
                .isEqualTo(recoveredBefore + 1.0d);
        assertThat(counterValue("chat_reconciliation_send_lost_total"))
                .isEqualTo(lostBefore + 1.0d);

        verify(gmailApiClientFactory).buildClientForTenant(tenantAId);
        verify(gmailApiClientFactory).buildClientForTenant(tenantBId);
        verify(tenantAListRequest).setQ("rfc822msgid:" + RECOVERED_GMAIL_MESSAGE_ID);
        verify(tenantBListRequest).setQ("rfc822msgid:" + LOST_GMAIL_MESSAGE_ID);

        String capturedLogs =
                logAppender.list.stream()
                        .map(ILoggingEvent::getFormattedMessage)
                        .reduce("", (left, right) -> left + "\n" + right);
        assertThat(capturedLogs)
                .contains("event=chat_reconciliation_swept")
                .contains("sweep=expired-lease action=confirmed")
                .contains("sweep=expired-lease action=failed")
                .contains("sweep=send-in-flight action=committed")
                .contains("sweep=send-in-flight action=failed")
                .doesNotContain(RECOVERED_GMAIL_MESSAGE_ID)
                .doesNotContain(LOST_GMAIL_MESSAGE_ID)
                .doesNotContain(RECENT_GMAIL_MESSAGE_ID);
    }

    private ExpiredLeaseSeeds seedExpiredLeaseCases(UUID tenantId) {
        UUID committedAuditChatId = seedChat(tenantId, "Expired committed audit");
        UUID missingAuditChatId = seedChat(tenantId, "Expired missing audit");
        UUID notExpiredChatId = seedChat(tenantId, "Processing not expired");
        UUID alreadyConfirmedChatId = seedChat(tenantId, "Already confirmed");
        UUID committedAuditMessageId = seedMessage(committedAuditChatId, tenantId);
        UUID missingAuditMessageId = seedMessage(missingAuditChatId, tenantId);
        UUID notExpiredMessageId = seedMessage(notExpiredChatId, tenantId);
        UUID alreadyConfirmedMessageId = seedMessage(alreadyConfirmedChatId, tenantId);

        UUID committedAuditPendingActionId =
                seedPendingAction(
                        tenantId,
                        committedAuditChatId,
                        committedAuditMessageId,
                        "tool-expired-committed",
                        "PROCESSING",
                        Instant.now().minusSeconds(120));
        seedAudit(
                tenantId,
                committedAuditChatId,
                "tool-expired-committed",
                "COMMITTED",
                null,
                null,
                Instant.now().minusSeconds(30));

        UUID missingAuditPendingActionId =
                seedPendingAction(
                        tenantId,
                        missingAuditChatId,
                        missingAuditMessageId,
                        "tool-expired-missing",
                        "PROCESSING",
                        Instant.now().minusSeconds(120));
        UUID notExpiredPendingActionId =
                seedPendingAction(
                        tenantId,
                        notExpiredChatId,
                        notExpiredMessageId,
                        "tool-not-expired",
                        "PROCESSING",
                        Instant.now().plusSeconds(120));
        UUID alreadyConfirmedPendingActionId =
                seedPendingAction(
                        tenantId,
                        alreadyConfirmedChatId,
                        alreadyConfirmedMessageId,
                        "tool-confirmed-expired",
                        "CONFIRMED",
                        Instant.now().minusSeconds(120));

        return new ExpiredLeaseSeeds(
                committedAuditPendingActionId,
                missingAuditPendingActionId,
                notExpiredPendingActionId,
                alreadyConfirmedPendingActionId);
    }

    private StaleSendSeeds seedStaleSendCases(UUID tenantAId, UUID tenantBId) {
        UUID recoveredChatId = seedChat(tenantAId, "Recovered send");
        UUID lostChatId = seedChat(tenantBId, "Lost send");
        UUID recentChatId = seedChat(tenantAId, "Recent send");
        UUID recoveredMessageId = seedMessage(recoveredChatId, tenantAId);
        UUID lostMessageId = seedMessage(lostChatId, tenantBId);
        UUID recentMessageId = seedMessage(recentChatId, tenantAId);

        UUID recoveredPendingActionId =
                seedPendingAction(
                        tenantAId,
                        recoveredChatId,
                        recoveredMessageId,
                        "tool-send-recovered",
                        "PROCESSING",
                        Instant.now().plusSeconds(120));
        UUID lostPendingActionId =
                seedPendingAction(
                        tenantBId,
                        lostChatId,
                        lostMessageId,
                        "tool-send-lost",
                        "PROCESSING",
                        Instant.now().plusSeconds(120));
        UUID recentPendingActionId =
                seedPendingAction(
                        tenantAId,
                        recentChatId,
                        recentMessageId,
                        "tool-send-recent",
                        "PROCESSING",
                        Instant.now().plusSeconds(120));

        UUID recoveredAuditId =
                seedAudit(
                        tenantAId,
                        recoveredChatId,
                        "tool-send-recovered",
                        "SEND_IN_FLIGHT",
                        RECOVERED_GMAIL_MESSAGE_ID,
                        Instant.now().minusSeconds(90),
                        null);
        UUID lostAuditId =
                seedAudit(
                        tenantBId,
                        lostChatId,
                        "tool-send-lost",
                        "SEND_IN_FLIGHT",
                        LOST_GMAIL_MESSAGE_ID,
                        Instant.now().minusSeconds(90),
                        null);
        UUID recentAuditId =
                seedAudit(
                        tenantAId,
                        recentChatId,
                        "tool-send-recent",
                        "SEND_IN_FLIGHT",
                        RECENT_GMAIL_MESSAGE_ID,
                        Instant.now().minusSeconds(30),
                        null);

        return new StaleSendSeeds(
                recoveredPendingActionId,
                lostPendingActionId,
                recentPendingActionId,
                recoveredAuditId,
                lostAuditId,
                recentAuditId);
    }

    private UUID seedTenant(String displayName) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)", tenantId, displayName);
        return tenantId;
    }

    private UUID seedChat(UUID tenantId, String title) {
        UUID chatId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into chat(id, tenant_id, title, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, 0)
                """,
                chatId,
                tenantId,
                title,
                Timestamp.from(Instant.now().minusSeconds(10)),
                Timestamp.from(Instant.now()));
        return chatId;
    }

    private UUID seedMessage(UUID chatId, UUID tenantId) {
        UUID chatMessageId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into chat_message(id, chat_id, tenant_id, role, parts, created_at)
                values (?, ?, ?, 'assistant',
                  '{"schemaVersion":1,"parts":[{"type":"text","text":"preview"}]}'::jsonb,
                  ?)
                """,
                chatMessageId,
                chatId,
                tenantId,
                Timestamp.from(Instant.now()));
        return chatMessageId;
    }

    private UUID seedPendingAction(
            UUID tenantId,
            UUID chatId,
            UUID chatMessageId,
            String toolCallId,
            String state,
            Instant expiresAt) {
        UUID pendingActionId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into assistant_pending_action(
                  id, chat_id, tenant_id, chat_message_id, tool_call_id, state, expires_at
                )
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                pendingActionId,
                chatId,
                tenantId,
                chatMessageId,
                toolCallId,
                state,
                Timestamp.from(expiresAt));
        return pendingActionId;
    }

    private UUID seedAudit(
            UUID tenantId,
            UUID chatId,
            String toolCallId,
            String state,
            String gmailMessageId,
            Instant inFlightAt,
            Instant sentAt) {
        UUID auditId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into assistant_action_audit(
                  id, chat_id, tenant_id, tool_call_id, tool_category, tool_name, state,
                  gmail_message_id, preview_snapshot, in_flight_at, sent_at
                )
                values (?, ?, ?, ?, 'confirmed-send', 'sendEmail', ?, ?, '{}'::jsonb, ?, ?)
                """,
                auditId,
                chatId,
                tenantId,
                toolCallId,
                state,
                gmailMessageId,
                inFlightAt == null ? null : Timestamp.from(inFlightAt),
                sentAt == null ? null : Timestamp.from(sentAt));
        return auditId;
    }

    private Gmail.Users.Messages.List configureGmailLookup(
            UUID tenantId, String gmailMessageId, boolean gmailHasMessage) throws Exception {
        Gmail gmail = mock(Gmail.class);
        Gmail.Users users = mock(Gmail.Users.class);
        Gmail.Users.Messages messages = mock(Gmail.Users.Messages.class);
        Gmail.Users.Messages.List messagesListRequest = mock(Gmail.Users.Messages.List.class);

        when(gmailApiClientFactory.buildClientForTenant(tenantId)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.messages()).thenReturn(messages);
        when(messages.list("me")).thenReturn(messagesListRequest);
        when(messagesListRequest.setQ("rfc822msgid:" + gmailMessageId))
                .thenReturn(messagesListRequest);
        List<Message> foundMessages =
                gmailHasMessage ? List.of(new Message().setId("gmail-hit-" + tenantId)) : null;
        when(messagesListRequest.execute())
                .thenReturn(new ListMessagesResponse().setMessages(foundMessages));
        return messagesListRequest;
    }

    private void assertPendingState(UUID pendingActionId, String expectedState) {
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select state from assistant_pending_action where id = ?",
                                String.class,
                                pendingActionId))
                .isEqualTo(expectedState);
    }

    private void assertAuditState(UUID auditId, String expectedState) {
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select state from assistant_action_audit where id = ?",
                                String.class,
                                auditId))
                .isEqualTo(expectedState);
    }

    private void assertAuditSentAtIsPresent(UUID auditId) {
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select sent_at from assistant_action_audit where id = ?",
                                Timestamp.class,
                                auditId))
                .isNotNull();
    }

    private void assertAuditSentAtIsNull(UUID auditId) {
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select sent_at from assistant_action_audit where id = ?",
                                Timestamp.class,
                                auditId))
                .isNull();
    }

    private double counterValue(String counterName) {
        Counter counter = meterRegistry.find(counterName).counter();
        assertThat(counter).as(counterName).isNotNull();
        return counter.count();
    }

    private record ExpiredLeaseSeeds(
            UUID committedAuditPendingActionId,
            UUID missingAuditPendingActionId,
            UUID notExpiredPendingActionId,
            UUID alreadyConfirmedPendingActionId) {}

    private record StaleSendSeeds(
            UUID recoveredPendingActionId,
            UUID lostPendingActionId,
            UUID recentPendingActionId,
            UUID recoveredAuditId,
            UUID lostAuditId,
            UUID recentAuditId) {}
}
