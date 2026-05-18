package com.zeromail.core.chat.confirm;

import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.chat.domain.ToolCategory;
import com.zeromail.core.chat.domain.parts.ChatMessageParts;
import com.zeromail.core.chat.domain.parts.Part;
import com.zeromail.core.chat.domain.parts.ToolCallPart;
import com.zeromail.core.chat.domain.parts.ToolOutputPart;
import com.zeromail.core.chat.exception.PendingActionNotFoundException;
import com.zeromail.core.chat.exception.StaleToolCallException;
import com.zeromail.core.chat.persistence.ChatPartsJsonConverter;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@SuppressWarnings("SqlResolve")
public class ConfirmationStateMachine {

    private final JdbcTemplate jdbcTemplate;
    private final ChatPartsJsonConverter chatPartsJsonConverter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public ConfirmationStateMachine(
            JdbcTemplate jdbcTemplate,
            ChatPartsJsonConverter chatPartsJsonConverter,
            ObjectMapper objectMapper) {
        this(jdbcTemplate, chatPartsJsonConverter, objectMapper, Clock.systemUTC());
    }

    ConfirmationStateMachine(
            JdbcTemplate jdbcTemplate,
            ChatPartsJsonConverter chatPartsJsonConverter,
            ObjectMapper objectMapper,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.chatPartsJsonConverter = chatPartsJsonConverter;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PendingAction loadPendingAction(UUID chatId, UUID tenantId, String toolCallId) {
        return jdbcTemplate
                .query(
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
                        (resultSet, _) -> {
                            ToolSnapshot toolSnapshot =
                                    toolSnapshot(
                                            resultSet.getString("parts_json"),
                                            resultSet.getString("tool_call_id"));
                            return new PendingAction(
                                    resultSet.getObject("id", UUID.class),
                                    resultSet.getObject("tenant_id", UUID.class),
                                    resultSet.getObject("chat_id", UUID.class),
                                    resultSet.getObject("chat_message_id", UUID.class),
                                    resultSet.getString("tool_call_id"),
                                    resultSet.getString("state"),
                                    resultSet.getTimestamp("parts_updated_at").toInstant(),
                                    resultSet.getString("draft_body"),
                                    toolSnapshot.toolName(),
                                    toolSnapshot.inputJson(),
                                    toolSnapshot.confirmationJson());
                        },
                        chatId,
                        tenantId,
                        toolCallId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new PendingActionNotFoundException(toolCallId));
    }

    @Transactional
    public Reservation reserve(
            UUID chatId,
            UUID tenantId,
            String toolCallId,
            Instant previouslyObservedPartsUpdatedAt) {
        Objects.requireNonNull(previouslyObservedPartsUpdatedAt, "partsUpdatedAt must not be null");
        int updatedRowCount =
                jdbcTemplate.update(
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
                        requireText(toolCallId, "toolCallId"),
                        chatId,
                        tenantId,
                        Timestamp.from(previouslyObservedPartsUpdatedAt));
        if (updatedRowCount != 1) {
            throw new StaleToolCallException(toolCallId);
        }
        PendingAction pendingAction = loadPendingAction(chatId, tenantId, toolCallId);
        return new Reservation(
                pendingAction.pendingActionId(),
                pendingAction.tenantId(),
                pendingAction.chatId(),
                pendingAction.chatMessageId(),
                pendingAction.toolCallId(),
                pendingAction.toolName(),
                pendingAction.toolCategory(),
                pendingAction.inputJson(),
                pendingAction.confirmationJson());
    }

    @Transactional
    public UUID recordSendInFlight(SendInFlightCommand command) {
        UUID auditId = UUID.randomUUID();
        Instant now = clock.instant();
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
                command.tenantId(),
                command.chatId(),
                command.toolCallId(),
                command.toolName().id(),
                command.recipientHash(),
                command.subjectHash(),
                command.gmailMessageId(),
                command.resultSummaryJson(),
                command.previewSnapshotJson(),
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now));
        return auditId;
    }

    @Transactional
    public void commitSendCompleted(UUID auditId, SendCommitCommand command) {
        Instant now = clock.instant();
        int changedRows =
                jdbcTemplate.update(
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
                        command.resultSummaryJson(),
                        Timestamp.from(now),
                        auditId,
                        command.tenantId());
        if (changedRows != 1) {
            throw new IllegalStateException("send audit row was not in flight");
        }
        updatePendingActionState(
                command.tenantId(), command.chatId(), command.toolCallId(), "CONFIRMED");
    }

    @Transactional
    public void commitSendFailed(UUID auditId, String toolCallId, String reasonCode) {
        AuditPointer auditPointer = auditPointer(auditId);
        int changedRows =
                jdbcTemplate.update(
                        """
                        UPDATE assistant_action_audit
                           SET state = 'FAILED',
                               result_summary_json = ?::jsonb,
                               updated_at = now(),
                               version = version + 1
                         WHERE id = ?
                           AND state = 'SEND_IN_FLIGHT'
                        """,
                        writeJson(Map.of("state", "failed", "reason", safeReasonCode(reasonCode))),
                        auditId);
        if (changedRows != 1) {
            return;
        }
        updatePendingActionState(
                auditPointer.tenantId(), auditPointer.chatId(), toolCallId, "FAILED");
    }

    @Transactional
    public void commitWriteReversible(WriteCommitCommand command) {
        Instant now = clock.instant();
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
                UUID.randomUUID(),
                command.tenantId(),
                command.chatId(),
                command.toolCallId(),
                categoryId(command.toolCategory()),
                command.toolName().id(),
                writeJson(command.resultSummary()),
                writeJson(command.previewSnapshot()),
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now));
        updatePendingActionState(
                command.tenantId(), command.chatId(), command.toolCallId(), "CONFIRMED");
    }

    @Transactional
    public void commitCanceled(UUID chatId, UUID tenantId, String toolCallId) {
        PendingAction pendingAction = loadPendingAction(chatId, tenantId, toolCallId);
        Instant now = clock.instant();
        int changedRows =
                jdbcTemplate.update(
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
        if (changedRows != 1) {
            throw new StaleToolCallException(toolCallId);
        }
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
                UUID.randomUUID(),
                tenantId,
                chatId,
                toolCallId,
                categoryId(pendingAction.toolCategory()),
                pendingAction.toolName().id(),
                writeJson(Map.of("state", "canceled")),
                writeJson(pendingAction.inputJson()),
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now));
    }

    @Transactional
    public void commitFailedFromReconciler(UUID chatId, UUID tenantId, String toolCallId) {
        updatePendingActionState(tenantId, chatId, toolCallId, "FAILED");
    }

    private void updatePendingActionState(
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

    private AuditPointer auditPointer(UUID auditId) {
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
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("send audit row was not found"));
    }

    private ToolSnapshot toolSnapshot(String partsJson, String toolCallId) {
        ChatMessageParts parts = chatPartsJsonConverter.fromJson(partsJson);
        for (Part part : parts.parts()) {
            if (part instanceof ToolOutputPart toolOutputPart
                    && toolCallId.equals(toolOutputPart.toolCallId())) {
                return new ToolSnapshot(
                        ChatToolName.fromId(toolOutputPart.toolName()),
                        toolOutputPart.inputJson(),
                        toolOutputPart.confirmationJson());
            }
            if (part instanceof ToolCallPart toolCallPart
                    && toolCallId.equals(toolCallPart.toolCallId())) {
                return new ToolSnapshot(
                        ChatToolName.fromId(toolCallPart.toolName()),
                        toolCallPart.inputJson(),
                        Map.of());
            }
        }
        throw new IllegalStateException("pending action tool call was not found in chat message");
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JacksonException jacksonException) {
            throw new IllegalArgumentException(
                    "confirmation state JSON is invalid", jacksonException);
        }
    }

    private static String safeReasonCode(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return "unknown";
        }
        return reasonCode.length() <= 80 ? reasonCode : reasonCode.substring(0, 80);
    }

    private static String categoryId(ToolCategory toolCategory) {
        return switch (toolCategory) {
            case READ -> "read";
            case WRITE_REVERSIBLE -> "write-reversible";
            case CONFIRM_REQUIRED -> "confirm-required";
            case CONFIRMED_SEND -> "confirmed-send";
        };
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmedValue = value.trim();
        if (trimmedValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmedValue;
    }

    public record PendingAction(
            UUID pendingActionId,
            UUID tenantId,
            UUID chatId,
            UUID chatMessageId,
            String toolCallId,
            String state,
            Instant partsUpdatedAt,
            String draftBody,
            ChatToolName toolName,
            Map<String, Object> inputJson,
            Map<String, Object> confirmationJson) {

        public ToolCategory toolCategory() {
            return toolName.category();
        }
    }

    public record Reservation(
            UUID pendingActionId,
            UUID tenantId,
            UUID chatId,
            UUID chatMessageId,
            String toolCallId,
            ChatToolName toolName,
            ToolCategory toolCategory,
            Map<String, Object> inputJson,
            Map<String, Object> confirmationJson) {}

    public record SendInFlightCommand(
            UUID tenantId,
            UUID chatId,
            String toolCallId,
            ChatToolName toolName,
            String gmailMessageId,
            String recipientHash,
            String subjectHash,
            String resultSummaryJson,
            String previewSnapshotJson) {}

    public record SendCommitCommand(
            UUID tenantId, UUID chatId, String toolCallId, String resultSummaryJson) {}

    public record WriteCommitCommand(
            UUID tenantId,
            UUID chatId,
            String toolCallId,
            ChatToolName toolName,
            ToolCategory toolCategory,
            Map<String, Object> resultSummary,
            Map<String, Object> previewSnapshot) {}

    private record ToolSnapshot(
            ChatToolName toolName,
            Map<String, Object> inputJson,
            Map<String, Object> confirmationJson) {}

    private record AuditPointer(UUID tenantId, UUID chatId) {}
}
