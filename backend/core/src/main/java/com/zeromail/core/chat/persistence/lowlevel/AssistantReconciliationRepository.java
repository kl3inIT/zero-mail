package com.zeromail.core.chat.persistence.lowlevel;

import com.zeromail.core.chat.projection.ExpiredAssistantPendingAction;
import com.zeromail.core.chat.projection.StaleAssistantSendAudit;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC repository for the assistant reconciler sweep. Owns all SQL that the api-layer scheduled
 * reconciler used to embed inline — both reads (find expired/stale) and writes (sweep terminal
 * state).
 *
 * <p>Per CONVENTIONS Section 1, the api layer must not embed SQL or hold {@code JdbcTemplate}
 * directly; this repository absorbs that coupling and keeps the reconciler thin.
 */
@Repository
public class AssistantReconciliationRepository {

    private final JdbcTemplate jdbcTemplate;

    public AssistantReconciliationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    public List<ExpiredAssistantPendingAction> findExpiredPendingActions(
            Instant now, int pageSize) {
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
                        new ExpiredAssistantPendingAction(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getObject("tenant_id", UUID.class),
                                resultSet.getObject("chat_id", UUID.class),
                                resultSet.getString("tool_call_id")),
                Timestamp.from(now),
                pageSize);
    }

    public int sweepPendingActionToTerminalState(
            UUID pendingActionId, UUID tenantId, String targetState) {
        return jdbcTemplate.update(
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
                pendingActionId,
                tenantId);
    }

    public boolean committedAuditExists(UUID tenantId, UUID chatId, String toolCallId) {
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
                        tenantId,
                        chatId,
                        toolCallId);
        return Boolean.TRUE.equals(exists);
    }

    public List<StaleAssistantSendAudit> findStaleSendAudits(Instant threshold, int pageSize) {
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
                        new StaleAssistantSendAudit(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getObject("tenant_id", UUID.class),
                                resultSet.getObject("chat_id", UUID.class),
                                resultSet.getString("tool_call_id"),
                                resultSet.getString("gmail_message_id")),
                Timestamp.from(threshold),
                pageSize);
    }

    public int markSendCommitted(UUID auditId, UUID tenantId, Instant sentAt) {
        return jdbcTemplate.update(
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
                auditId,
                tenantId);
    }

    public int markSendFailed(UUID auditId, UUID tenantId) {
        return jdbcTemplate.update(
                """
                UPDATE assistant_action_audit
                   SET state = 'FAILED',
                       updated_at = now(),
                       version = version + 1
                 WHERE id = ?
                   AND tenant_id = ?
                   AND state = 'SEND_IN_FLIGHT'
                """,
                auditId,
                tenantId);
    }

    public void updatePendingActionState(
            UUID tenantId, UUID chatId, String toolCallId, String state) {
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
                tenantId,
                chatId,
                toolCallId,
                state);
    }
}
