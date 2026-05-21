package com.zeromail.core.chat.confirm;

import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.chat.domain.ToolCategory;
import com.zeromail.core.chat.domain.event.AssistantSendCompleted;
import com.zeromail.core.chat.domain.parts.ChatMessageParts;
import com.zeromail.core.chat.domain.parts.Part;
import com.zeromail.core.chat.domain.parts.ToolCallPart;
import com.zeromail.core.chat.domain.parts.ToolOutputPart;
import com.zeromail.core.chat.exception.PendingActionNotFoundException;
import com.zeromail.core.chat.exception.StaleToolCallException;
import com.zeromail.core.chat.persistence.ChatPartsJsonConverter;
import com.zeromail.core.chat.persistence.lowlevel.ConfirmationStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ConfirmationStateMachine {

    private final ConfirmationStateRepository confirmationStateRepository;
    private final ChatPartsJsonConverter chatPartsJsonConverter;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    @Autowired
    public ConfirmationStateMachine(
            ConfirmationStateRepository confirmationStateRepository,
            ChatPartsJsonConverter chatPartsJsonConverter,
            ObjectMapper objectMapper,
            ApplicationEventPublisher applicationEventPublisher) {
        this(
                confirmationStateRepository,
                chatPartsJsonConverter,
                objectMapper,
                applicationEventPublisher,
                Clock.systemUTC());
    }

    ConfirmationStateMachine(
            ConfirmationStateRepository confirmationStateRepository,
            ChatPartsJsonConverter chatPartsJsonConverter,
            ObjectMapper objectMapper,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock) {
        this.confirmationStateRepository =
                Objects.requireNonNull(
                        confirmationStateRepository,
                        "confirmationStateRepository must not be null");
        this.chatPartsJsonConverter =
                Objects.requireNonNull(
                        chatPartsJsonConverter, "chatPartsJsonConverter must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.applicationEventPublisher =
                Objects.requireNonNull(
                        applicationEventPublisher, "applicationEventPublisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(readOnly = true)
    public PendingAction loadPendingAction(UUID chatId, UUID tenantId, String toolCallId) {
        ConfirmationStateRepository.PendingActionRow pendingActionRow =
                confirmationStateRepository
                        .findPendingActionRow(chatId, tenantId, toolCallId)
                        .orElseThrow(() -> new PendingActionNotFoundException(toolCallId));
        ToolSnapshot toolSnapshot =
                toolSnapshot(pendingActionRow.partsJson(), pendingActionRow.toolCallId());
        return new PendingAction(
                pendingActionRow.pendingActionId(),
                pendingActionRow.tenantId(),
                pendingActionRow.chatId(),
                pendingActionRow.chatMessageId(),
                pendingActionRow.toolCallId(),
                pendingActionRow.state(),
                pendingActionRow.partsUpdatedAt(),
                pendingActionRow.draftBody(),
                toolSnapshot.toolName(),
                toolSnapshot.inputJson(),
                toolSnapshot.confirmationJson());
    }

    @Transactional
    public Reservation reserve(
            UUID chatId,
            UUID tenantId,
            String toolCallId,
            Instant previouslyObservedPartsUpdatedAt) {
        Objects.requireNonNull(previouslyObservedPartsUpdatedAt, "partsUpdatedAt must not be null");
        int updatedRowCount =
                confirmationStateRepository.reservePendingAction(
                        chatId,
                        tenantId,
                        requireText(toolCallId, "toolCallId"),
                        previouslyObservedPartsUpdatedAt);
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

    /**
     * Reverts a reservation from PROCESSING back to PENDING. Called when synchronous validation
     * after reserve() fails (e.g., toCommand throws) so the row does not get stuck in PROCESSING
     * until the reconciliation cron times it out (WR-01). Returns true if the revert succeeded;
     * false if the row was already advanced past PROCESSING (e.g., concurrent reconciliation), in
     * which case the caller should not treat this as an error.
     */
    @Transactional
    public boolean revertReservation(UUID chatId, UUID tenantId, String toolCallId) {
        int updatedRowCount =
                confirmationStateRepository.revertReservation(
                        chatId, tenantId, requireText(toolCallId, "toolCallId"));
        return updatedRowCount == 1;
    }

    @Transactional
    public UUID recordSendInFlight(SendInFlightCommand command) {
        UUID auditId = UUID.randomUUID();
        Instant now = clock.instant();
        confirmationStateRepository.insertSendInFlightAudit(
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
                now);
        return auditId;
    }

    @Transactional
    public boolean commitSendCompleted(UUID auditId, SendCommitCommand command) {
        Instant now = clock.instant();
        int changedRows =
                confirmationStateRepository.markSendCommittedAudit(
                        auditId, command.tenantId(), command.resultSummaryJson(), now);
        if (changedRows != 1) {
            // Idempotent: the reconciliation cron (or a retry) may have already moved this audit
            // row to COMMITTED. Confirm the row is in a terminal COMMITTED state and return false
            // so the caller does not double-publish the AssistantSendCompleted event. If the row
            // is genuinely missing or in an unexpected state, fail loud.
            String currentState =
                    confirmationStateRepository
                            .findAuditState(auditId, command.tenantId())
                            .orElse(null);
            if ("COMMITTED".equals(currentState)) {
                return false;
            }
            throw new IllegalStateException(
                    "send audit row was not in flight (current state=" + currentState + ")");
        }
        confirmationStateRepository.updatePendingActionState(
                command.tenantId(), command.chatId(), command.toolCallId(), "CONFIRMED");
        // Publish the domain event inside this transaction. Spring's
        // @TransactionalEventListener(AFTER_COMMIT) listeners require an active tx at publish
        // time; publishing here guarantees the event fires only on real DB commit, and not at
        // all if the UPDATE rolls back (CR-02 / WR-10).
        applicationEventPublisher.publishEvent(
                new AssistantSendCompleted(
                        command.tenantId().toString(),
                        command.chatId(),
                        command.toolCallId(),
                        auditId,
                        now));
        return true;
    }

    @Transactional
    public void commitSendFailed(UUID auditId, String toolCallId, String reasonCode) {
        ConfirmationStateRepository.AuditPointer auditPointer =
                confirmationStateRepository
                        .findAuditPointer(auditId)
                        .orElseThrow(
                                () -> new IllegalStateException("send audit row was not found"));
        int changedRows =
                confirmationStateRepository.markSendFailedAudit(
                        auditId,
                        writeJson(Map.of("state", "failed", "reason", safeReasonCode(reasonCode))));
        if (changedRows != 1) {
            return;
        }
        confirmationStateRepository.updatePendingActionState(
                auditPointer.tenantId(), auditPointer.chatId(), toolCallId, "FAILED");
    }

    @Transactional
    public void commitWriteReversible(WriteCommitCommand command) {
        Instant now = clock.instant();
        confirmationStateRepository.insertCommittedAudit(
                UUID.randomUUID(),
                command.tenantId(),
                command.chatId(),
                command.toolCallId(),
                categoryId(command.toolCategory()),
                command.toolName().id(),
                writeJson(command.resultSummary()),
                writeJson(command.previewSnapshot()),
                now);
        confirmationStateRepository.updatePendingActionState(
                command.tenantId(), command.chatId(), command.toolCallId(), "CONFIRMED");
    }

    @Transactional
    public void commitCanceled(UUID chatId, UUID tenantId, String toolCallId) {
        PendingAction pendingAction = loadPendingAction(chatId, tenantId, toolCallId);
        Instant now = clock.instant();
        int changedRows =
                confirmationStateRepository.markPendingActionCanceled(tenantId, chatId, toolCallId);
        if (changedRows != 1) {
            throw new StaleToolCallException(toolCallId);
        }
        confirmationStateRepository.insertCanceledAudit(
                UUID.randomUUID(),
                tenantId,
                chatId,
                toolCallId,
                categoryId(pendingAction.toolCategory()),
                pendingAction.toolName().id(),
                writeJson(Map.of("state", "canceled")),
                writeJson(pendingAction.inputJson()),
                now);
    }

    @Transactional
    public void commitFailedFromReconciler(UUID chatId, UUID tenantId, String toolCallId) {
        confirmationStateRepository.updatePendingActionState(
                tenantId, chatId, toolCallId, "FAILED");
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
}
