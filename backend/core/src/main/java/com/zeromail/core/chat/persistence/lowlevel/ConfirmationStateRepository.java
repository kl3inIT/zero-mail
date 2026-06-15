package com.zeromail.core.chat.persistence.lowlevel;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC reads + writes against {@code assistant_pending_action} and {@code assistant_action_audit}
 * for the confirmation state machine. Per CONVENTIONS Section 1, the use-case service must not
 * embed SQL; the state-machine semantics (transitions, idempotency decisions, JSON serialization)
 * stay in the service while raw row I/O lives here.
 */
@Repository
public class ConfirmationStateRepository {

    private final JdbcTemplate jdbcTemplate;

    public ConfirmationStateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    public Optional<PendingActionRow> findPendingActionRow(
            UUID chatId, UUID tenantId, String toolCallId) {
        List<PendingActionRow> rows =
                jdbcTemplate.query(
                        """
                        SELECT pending_action.id,
                               pending_action.tenant_id,
                               pending_action.chat_id,
                               pending_action.chat_message_id,
                               pending_action.tool_call_id,
                               pending_action.state,
                               pending_action.parts_updated_at,
                               pending_action.draft_body,
                               chat_message.parts::text AS parts_json
                          FROM assistant_pending_action pending_action
                          JOIN chat_message chat_message
                            ON chat_message.id = pending_action.chat_message_id
                         WHERE pending_action.chat_id = ?
                           AND pending_action.tenant_id = ?
                           AND pending_action.tool_call_id = ?
                        """,
                        (resultSet, _) ->
                                new PendingActionRow(
                                        resultSet.getObject("id", UUID.class),
                                        resultSet.getObject("tenant_id", UUID.class),
                                        resultSet.getObject("chat_id", UUID.class),
                                        resultSet.getObject("chat_message_id", UUID.class),
                                        resultSet.getString("tool_call_id"),
                                        resultSet.getString("state"),
                                        resultSet.getTimestamp("parts_updated_at").toInstant(),
                                        resultSet.getString("draft_body"),
                                        resultSet.getString("parts_json")),
                        chatId,
                        tenantId,
                        toolCallId);
        return rows.stream().findFirst();
    }

    public Optional<PendingActionRow> findPendingActionRowByChatMessageId(
            UUID tenantId, UUID chatMessageId) {
        List<PendingActionRow> rows =
                jdbcTemplate.query(
                        """
                        SELECT pending_action.id,
                               pending_action.tenant_id,
                               pending_action.chat_id,
                               pending_action.chat_message_id,
                               pending_action.tool_call_id,
                               pending_action.state,
                               pending_action.parts_updated_at,
                               pending_action.draft_body,
                               chat_message.parts::text AS parts_json
                          FROM assistant_pending_action pending_action
                          JOIN chat_message chat_message
                            ON chat_message.id = pending_action.chat_message_id
                         WHERE pending_action.tenant_id = ?
                           AND pending_action.chat_message_id = ?
                        """,
                        (resultSet, _) ->
                                new PendingActionRow(
                                        resultSet.getObject("id", UUID.class),
                                        resultSet.getObject("tenant_id", UUID.class),
                                        resultSet.getObject("chat_id", UUID.class),
                                        resultSet.getObject("chat_message_id", UUID.class),
                                        resultSet.getString("tool_call_id"),
                                        resultSet.getString("state"),
                                        resultSet.getTimestamp("parts_updated_at").toInstant(),
                                        resultSet.getString("draft_body"),
                                        resultSet.getString("parts_json")),
                        tenantId,
                        chatMessageId);
        return rows.stream().findFirst();
    }

    public int reservePendingAction(
            UUID chatId,
            UUID tenantId,
            String toolCallId,
            Instant previouslyObservedPartsUpdatedAt) {
        return jdbcTemplate.update(
                """
                UPDATE assistant_pending_action
                   SET state = 'PROCESSING',
                       updated_at = now(),
                       version = version + 1
                 WHERE tool_call_id = ?
                   AND chat_id = ?
                   AND tenant_id = ?
                   AND parts_updated_at = ?
                   AND state = 'PENDING'
                """,
                toolCallId,
                chatId,
                tenantId,
                Timestamp.from(previouslyObservedPartsUpdatedAt));
    }

    public int revertReservation(UUID chatId, UUID tenantId, String toolCallId) {
        return jdbcTemplate.update(
                """
                UPDATE assistant_pending_action
                   SET state = 'PENDING',
                       updated_at = now(),
                       version = version + 1
                 WHERE tool_call_id = ?
                   AND chat_id = ?
                   AND tenant_id = ?
                   AND state = 'PROCESSING'
                """,
                toolCallId,
                chatId,
                tenantId);
    }

    public void insertSendInFlightAudit(
            UUID auditId,
            UUID tenantId,
            UUID chatId,
            String toolCallId,
            String toolNameId,
            String recipientHash,
            String subjectHash,
            String gmailMessageId,
            String resultSummaryJson,
            String previewSnapshotJson,
            Instant now) {
        Timestamp nowTimestamp = Timestamp.from(now);
        jdbcTemplate.update(
                """
                INSERT INTO assistant_action_audit (
                    id,
                    tenant_id,
                    chat_id,
                    tool_call_id,
                    tool_category,
                    tool_name,
                    state,
                    recipient_hash,
                    subject_hash,
                    gmail_message_id,
                    result_summary_json,
                    preview_snapshot,
                    in_flight_at,
                    created_at,
                    updated_at,
                    version
                )
                VALUES (?, ?, ?, ?, 'confirmed-send', ?, 'SEND_IN_FLIGHT', ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, 0)
                """,
                auditId,
                tenantId,
                chatId,
                toolCallId,
                toolNameId,
                recipientHash,
                subjectHash,
                gmailMessageId,
                resultSummaryJson,
                previewSnapshotJson,
                nowTimestamp,
                nowTimestamp,
                nowTimestamp);
    }

    public int markSendCommittedAudit(
            UUID auditId, UUID tenantId, String resultSummaryJson, Instant sentAt) {
        return jdbcTemplate.update(
                """
                UPDATE assistant_action_audit
                   SET state = 'COMMITTED',
                       result_summary_json = ?::jsonb,
                       sent_at = ?,
                       updated_at = now(),
                       version = version + 1
                 WHERE id = ?
                   AND tenant_id = ?
                   AND state = 'SEND_IN_FLIGHT'
                """,
                resultSummaryJson,
                Timestamp.from(sentAt),
                auditId,
                tenantId);
    }

    public Optional<String> findAuditState(UUID auditId, UUID tenantId) {
        return jdbcTemplate
                .query(
                        """
                        SELECT state
                          FROM assistant_action_audit
                         WHERE id = ?
                           AND tenant_id = ?
                        """,
                        (resultSet, _) -> resultSet.getString("state"),
                        auditId,
                        tenantId)
                .stream()
                .findFirst();
    }

    public int markSendFailedAudit(UUID auditId, String resultSummaryJson) {
        return jdbcTemplate.update(
                """
                UPDATE assistant_action_audit
                   SET state = 'FAILED',
                       result_summary_json = ?::jsonb,
                       updated_at = now(),
                       version = version + 1
                 WHERE id = ?
                   AND state = 'SEND_IN_FLIGHT'
                """,
                resultSummaryJson,
                auditId);
    }

    public Optional<AuditPointer> findAuditPointer(UUID auditId) {
        return jdbcTemplate
                .query(
                        """
                        SELECT tenant_id, chat_id
                          FROM assistant_action_audit
                         WHERE id = ?
                        """,
                        (resultSet, _) ->
                                new AuditPointer(
                                        resultSet.getObject("tenant_id", UUID.class),
                                        resultSet.getObject("chat_id", UUID.class)),
                        auditId)
                .stream()
                .findFirst();
    }

    public void insertCommittedAudit(
            UUID auditId,
            UUID tenantId,
            UUID chatId,
            String toolCallId,
            String toolCategoryId,
            String toolNameId,
            String resultSummaryJson,
            String previewSnapshotJson,
            Instant now) {
        Timestamp nowTimestamp = Timestamp.from(now);
        jdbcTemplate.update(
                """
                INSERT INTO assistant_action_audit (
                    id,
                    tenant_id,
                    chat_id,
                    tool_call_id,
                    tool_category,
                    tool_name,
                    state,
                    result_summary_json,
                    preview_snapshot,
                    sent_at,
                    created_at,
                    updated_at,
                    version
                )
                VALUES (?, ?, ?, ?, ?, ?, 'COMMITTED', ?::jsonb, ?::jsonb, ?, ?, ?, 0)
                """,
                auditId,
                tenantId,
                chatId,
                toolCallId,
                toolCategoryId,
                toolNameId,
                resultSummaryJson,
                previewSnapshotJson,
                nowTimestamp,
                nowTimestamp,
                nowTimestamp);
    }

    public int markPendingActionCanceled(UUID tenantId, UUID chatId, String toolCallId) {
        return jdbcTemplate.update(
                """
                UPDATE assistant_pending_action
                   SET state = 'CANCELED',
                       updated_at = now(),
                       version = version + 1
                 WHERE tenant_id = ?
                   AND chat_id = ?
                   AND tool_call_id = ?
                   AND state = 'PENDING'
                """,
                tenantId,
                chatId,
                toolCallId);
    }

    public void insertCanceledAudit(
            UUID auditId,
            UUID tenantId,
            UUID chatId,
            String toolCallId,
            String toolCategoryId,
            String toolNameId,
            String resultSummaryJson,
            String previewSnapshotJson,
            Instant now) {
        Timestamp nowTimestamp = Timestamp.from(now);
        jdbcTemplate.update(
                """
                INSERT INTO assistant_action_audit (
                    id,
                    tenant_id,
                    chat_id,
                    tool_call_id,
                    tool_category,
                    tool_name,
                    state,
                    result_summary_json,
                    preview_snapshot,
                    sent_at,
                    created_at,
                    updated_at,
                    version
                )
                VALUES (?, ?, ?, ?, ?, ?, 'COMMITTED', ?::jsonb, ?::jsonb, ?, ?, ?, 0)
                ON CONFLICT (chat_id, tool_call_id) DO NOTHING
                """,
                auditId,
                tenantId,
                chatId,
                toolCallId,
                toolCategoryId,
                toolNameId,
                resultSummaryJson,
                previewSnapshotJson,
                nowTimestamp,
                nowTimestamp,
                nowTimestamp);
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

    public record PendingActionRow(
            UUID pendingActionId,
            UUID tenantId,
            UUID chatId,
            UUID chatMessageId,
            String toolCallId,
            String state,
            Instant partsUpdatedAt,
            String draftBody,
            String partsJson) {}

    public record AuditPointer(UUID tenantId, UUID chatId) {}
}
