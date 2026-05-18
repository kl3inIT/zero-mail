package com.zeromail.api.chat;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.tenant.TenantContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reconciles assistant confirmation state after crashes.
 *
 * <p>There is one permanent residual gap: Gmail can accept a generated message after the server has
 * failed to commit the SEND_IN_FLIGHT audit row to Postgres. Without a committed audit row,
 * server-side reconciliation has no durable key to discover that orphaned delivery; this is
 * inherent to coordinating Gmail and Postgres without two-phase commit.
 */
@Component
@SuppressWarnings("SqlResolve")
public class AssistantPendingActionReconciler {

    private static final Logger log =
            LoggerFactory.getLogger(AssistantPendingActionReconciler.class);
    private static final int PAGE_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final GmailApiClientFactory gmailApiClientFactory;
    private final Clock clock;
    private final Counter residualLeasesCounter;
    private final Counter auditStateMismatchCounter;
    private final Counter sendRecoveredCounter;
    private final Counter sendLostCounter;

    @Autowired
    public AssistantPendingActionReconciler(
            JdbcTemplate jdbcTemplate,
            GmailApiClientFactory gmailApiClientFactory,
            MeterRegistry meterRegistry) {
        this(jdbcTemplate, gmailApiClientFactory, meterRegistry, Clock.systemUTC());
    }

    AssistantPendingActionReconciler(
            JdbcTemplate jdbcTemplate,
            GmailApiClientFactory gmailApiClientFactory,
            MeterRegistry meterRegistry,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.gmailApiClientFactory = gmailApiClientFactory;
        this.clock = clock;
        this.residualLeasesCounter =
                Counter.builder("chat_reconciliation_residual_leases_total")
                        .description("Expired assistant pending-action processing leases swept")
                        .register(meterRegistry);
        this.auditStateMismatchCounter =
                Counter.builder("chat_reconciliation_audit_vs_state_mismatch_total")
                        .description("Pending actions reconciled from committed audit rows")
                        .register(meterRegistry);
        this.sendRecoveredCounter =
                Counter.builder("chat_reconciliation_send_recovered_total")
                        .description("Stale assistant sends recovered as committed from Gmail")
                        .register(meterRegistry);
        this.sendLostCounter =
                Counter.builder("chat_reconciliation_send_lost_total")
                        .description("Stale assistant sends marked failed after Gmail lookup miss")
                        .register(meterRegistry);
    }

    @PostConstruct
    void logStartupResidualGap() {
        log.info("event=chat_reconciler_started tenantId=- note=residual-gap-orphaned-email");
    }

    @Scheduled(fixedRate = 300_000L, initialDelay = 60_000L)
    public void reconcile() {
        reconcileExpiredLeases();
        reconcileStaleSendInFlight();
    }

    void reconcileExpiredLeases() {
        List<ExpiredPendingAction> expiredPendingActions = findExpiredPendingActions();
        for (ExpiredPendingAction expiredPendingAction : expiredPendingActions) {
            TenantContext.runWith(
                    expiredPendingAction.tenantId(),
                    () -> reconcileExpiredLease(expiredPendingAction));
        }
    }

    void reconcileStaleSendInFlight() {
        List<StaleSendAudit> staleSendAudits = findStaleSendAudits();
        for (StaleSendAudit staleSendAudit : staleSendAudits) {
            TenantContext.runWith(
                    staleSendAudit.tenantId(), () -> reconcileStaleSendAudit(staleSendAudit));
        }
    }

    private List<ExpiredPendingAction> findExpiredPendingActions() {
        return jdbcTemplate.query(
                """
                SELECT id, tenant_id, chat_id, tool_call_id
                  FROM assistant_pending_action
                 WHERE state = 'PROCESSING'
                   AND expires_at < ?
                 ORDER BY expires_at, id
                 LIMIT ?
                """,
                (resultSet, _) ->
                        new ExpiredPendingAction(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getObject("tenant_id", UUID.class),
                                resultSet.getObject("chat_id", UUID.class),
                                resultSet.getString("tool_call_id")),
                Timestamp.from(clock.instant()),
                PAGE_SIZE);
    }

    private void reconcileExpiredLease(ExpiredPendingAction expiredPendingAction) {
        residualLeasesCounter.increment();
        boolean committedAuditExists = committedAuditExists(expiredPendingAction);
        String targetState = committedAuditExists ? "CONFIRMED" : "FAILED";
        int changedRows =
                jdbcTemplate.update(
                        """
                        UPDATE assistant_pending_action
                           SET state = ?,
                               updated_at = now(),
                               version = version + 1
                         WHERE id = ?
                           AND tenant_id = ?
                           AND state = 'PROCESSING'
                        """,
                        targetState,
                        expiredPendingAction.pendingActionId(),
                        expiredPendingAction.tenantId());
        if (changedRows == 0) {
            return;
        }
        if (committedAuditExists) {
            auditStateMismatchCounter.increment();
        }
        log.info(
                "event=chat_reconciliation_swept tenantId={} chatId={} sweep=expired-lease action={}",
                expiredPendingAction.tenantId(),
                expiredPendingAction.chatId(),
                committedAuditExists ? "confirmed" : "failed");
    }

    private boolean committedAuditExists(ExpiredPendingAction expiredPendingAction) {
        Boolean exists =
                jdbcTemplate.queryForObject(
                        """
                        SELECT EXISTS (
                            SELECT 1
                              FROM assistant_action_audit
                             WHERE tenant_id = ?
                               AND chat_id = ?
                               AND tool_call_id = ?
                               AND state = 'COMMITTED'
                        )
                        """,
                        Boolean.class,
                        expiredPendingAction.tenantId(),
                        expiredPendingAction.chatId(),
                        expiredPendingAction.toolCallId());
        return Boolean.TRUE.equals(exists);
    }

    private List<StaleSendAudit> findStaleSendAudits() {
        return jdbcTemplate.query(
                """
                SELECT id, tenant_id, chat_id, tool_call_id, gmail_message_id
                  FROM assistant_action_audit
                 WHERE state = 'SEND_IN_FLIGHT'
                   AND in_flight_at < ?
                   AND gmail_message_id IS NOT NULL
                 ORDER BY in_flight_at, id
                 LIMIT ?
                """,
                (resultSet, _) ->
                        new StaleSendAudit(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getObject("tenant_id", UUID.class),
                                resultSet.getObject("chat_id", UUID.class),
                                resultSet.getString("tool_call_id"),
                                resultSet.getString("gmail_message_id")),
                Timestamp.from(clock.instant().minusSeconds(60)),
                PAGE_SIZE);
    }

    private void reconcileStaleSendAudit(StaleSendAudit staleSendAudit) {
        try {
            if (gmailHasMessage(staleSendAudit)) {
                markSendCommitted(staleSendAudit);
            } else {
                markSendFailed(staleSendAudit);
            }
        } catch (IOException gmailLookupFailure) {
            log.warn(
                    "event=chat_reconciliation_gmail_lookup_failed tenantId={} chatId={} errorClass={}",
                    staleSendAudit.tenantId(),
                    staleSendAudit.chatId(),
                    gmailLookupFailure.getClass().getSimpleName());
        }
    }

    private boolean gmailHasMessage(StaleSendAudit staleSendAudit) throws IOException {
        Gmail gmail = gmailApiClientFactory.buildClientForTenant(staleSendAudit.tenantId());
        ListMessagesResponse response =
                gmail.users()
                        .messages()
                        .list("me")
                        .setQ("rfc822msgid:" + staleSendAudit.gmailMessageId())
                        .execute();
        List<Message> messages = response == null ? null : response.getMessages();
        return messages != null && !messages.isEmpty();
    }

    private void markSendCommitted(StaleSendAudit staleSendAudit) {
        Instant sentAt = clock.instant();
        int changedRows =
                jdbcTemplate.update(
                        """
                        UPDATE assistant_action_audit
                           SET state = 'COMMITTED',
                               sent_at = ?,
                               updated_at = now(),
                               version = version + 1
                         WHERE id = ?
                           AND tenant_id = ?
                           AND state = 'SEND_IN_FLIGHT'
                        """,
                        Timestamp.from(sentAt),
                        staleSendAudit.auditId(),
                        staleSendAudit.tenantId());
        if (changedRows == 0) {
            return;
        }
        updatePendingActionState(staleSendAudit, "CONFIRMED");
        sendRecoveredCounter.increment();
        log.info(
                "event=chat_reconciliation_swept tenantId={} chatId={} sweep=send-in-flight action=committed",
                staleSendAudit.tenantId(),
                staleSendAudit.chatId());
    }

    private void markSendFailed(StaleSendAudit staleSendAudit) {
        int changedRows =
                jdbcTemplate.update(
                        """
                        UPDATE assistant_action_audit
                           SET state = 'FAILED',
                               updated_at = now(),
                               version = version + 1
                         WHERE id = ?
                           AND tenant_id = ?
                           AND state = 'SEND_IN_FLIGHT'
                        """,
                        staleSendAudit.auditId(),
                        staleSendAudit.tenantId());
        if (changedRows == 0) {
            return;
        }
        updatePendingActionState(staleSendAudit, "FAILED");
        sendLostCounter.increment();
        log.info(
                "event=chat_reconciliation_swept tenantId={} chatId={} sweep=send-in-flight action=failed",
                staleSendAudit.tenantId(),
                staleSendAudit.chatId());
    }

    private void updatePendingActionState(StaleSendAudit staleSendAudit, String state) {
        jdbcTemplate.update(
                """
                UPDATE assistant_pending_action
                   SET state = ?,
                       updated_at = now(),
                       version = version + 1
                 WHERE tenant_id = ?
                   AND chat_id = ?
                   AND tool_call_id = ?
                   AND state <> ?
                """,
                state,
                staleSendAudit.tenantId(),
                staleSendAudit.chatId(),
                staleSendAudit.toolCallId(),
                state);
    }

    private record ExpiredPendingAction(
            UUID pendingActionId, UUID tenantId, UUID chatId, String toolCallId) {}

    private record StaleSendAudit(
            UUID auditId, UUID tenantId, UUID chatId, String toolCallId, String gmailMessageId) {}
}
