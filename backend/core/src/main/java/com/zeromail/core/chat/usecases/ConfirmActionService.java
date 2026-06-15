package com.zeromail.core.chat.usecases;

import com.zeromail.core.chat.confirm.ConfirmationLeaseService;
import com.zeromail.core.chat.confirm.ConfirmationStateMachine;
import com.zeromail.core.chat.confirm.ConfirmationStateMachine.PendingAction;
import com.zeromail.core.chat.confirm.ConfirmationStateMachine.Reservation;
import com.zeromail.core.chat.confirm.send.AssistantSendCommand;
import com.zeromail.core.chat.confirm.send.AssistantSendExecutor;
import com.zeromail.core.chat.confirm.send.AssistantWriteCommand;
import com.zeromail.core.chat.confirm.send.AssistantWriteExecutor;
import com.zeromail.core.chat.domain.ToolCategory;
import com.zeromail.core.chat.exception.ConfirmationLeaseConflictException;
import com.zeromail.core.chat.usecases.tools.ConfirmedSendToolHandlers;
import com.zeromail.core.tenant.TenantContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ConfirmActionService {

    private final ConfirmationStateMachine confirmationStateMachine;
    private final ConfirmationLeaseService confirmationLeaseService;
    private final ConfirmedSendToolHandlers confirmedSendToolHandlers;
    private final AssistantSendExecutor assistantSendExecutor;
    private final AssistantWriteExecutor assistantWriteExecutor;

    public ConfirmActionService(
            ConfirmationStateMachine confirmationStateMachine,
            ConfirmationLeaseService confirmationLeaseService,
            ConfirmedSendToolHandlers confirmedSendToolHandlers,
            AssistantSendExecutor assistantSendExecutor,
            AssistantWriteExecutor assistantWriteExecutor) {
        this.confirmationStateMachine = confirmationStateMachine;
        this.confirmationLeaseService = confirmationLeaseService;
        this.confirmedSendToolHandlers = confirmedSendToolHandlers;
        this.assistantSendExecutor = assistantSendExecutor;
        this.assistantWriteExecutor = assistantWriteExecutor;
    }

    public ConfirmActionResult confirm(
            UUID chatId,
            String toolCallId,
            boolean vipAcknowledged,
            Map<String, Object> contentOverride) {
        UUID tenantId = TenantContext.currentTenantUuid();
        PendingAction pendingAction =
                confirmationStateMachine.loadPendingAction(chatId, tenantId, toolCallId);
        requirePendingConfirmable(pendingAction);
        String processInstanceId = newProcessInstanceId();
        acquireLease(chatId, toolCallId, processInstanceId);
        boolean executorWillReleaseLease = false;
        try {
            Reservation reservation =
                    confirmationStateMachine.reserve(
                            chatId, tenantId, toolCallId, pendingAction.partsUpdatedAt());
            if (reservation.toolCategory() == ToolCategory.CONFIRMED_SEND) {
                AssistantSendCommand sendCommand;
                try {
                    sendCommand =
                            confirmedSendToolHandlers.toCommand(
                                    reservation,
                                    vipAcknowledged,
                                    safeOverride(contentOverride),
                                    processInstanceId);
                } catch (RuntimeException toCommandFailure) {
                    // Synchronous validation (toCommand) failed AFTER reserve() committed
                    // state=PROCESSING. Revert back to PENDING so the user is not stuck on an
                    // infinite spinner until the 5-minute reconciliation cron fires (WR-01).
                    confirmationStateMachine.revertReservation(chatId, tenantId, toolCallId);
                    throw toCommandFailure;
                }
                executorWillReleaseLease = true;
                AssistantSendExecutor.AssistantSendResult sendResult =
                        assistantSendExecutor.execute(sendCommand);
                return new ConfirmActionResult(sendResult.state(), sendResult.resultSummary());
            }
            executorWillReleaseLease = true;
            AssistantWriteExecutor.AssistantWriteResult writeResult =
                    assistantWriteExecutor.execute(
                            new AssistantWriteCommand(
                                    reservation.tenantId(),
                                    reservation.chatId(),
                                    reservation.toolCallId(),
                                    reservation.toolName(),
                                    effectiveInput(reservation, contentOverride),
                                    reservation.confirmationJson(),
                                    processInstanceId));
            return new ConfirmActionResult(writeResult.state(), writeResult.resultSummary());
        } finally {
            if (!executorWillReleaseLease) {
                confirmationLeaseService.release(chatId, toolCallId, processInstanceId);
            }
        }
    }

    public ConfirmActionResult confirmByChatMessageId(
            UUID chatMessageId, boolean vipAcknowledged, Map<String, Object> contentOverride) {
        UUID tenantId = TenantContext.currentTenantUuid();
        PendingAction pendingAction =
                confirmationStateMachine.loadPendingActionByChatMessageId(tenantId, chatMessageId);
        return confirm(
                pendingAction.chatId(),
                pendingAction.toolCallId(),
                vipAcknowledged,
                contentOverride);
    }

    public ConfirmActionResult cancel(UUID chatId, String toolCallId) {
        UUID tenantId = TenantContext.currentTenantUuid();
        PendingAction pendingAction =
                confirmationStateMachine.loadPendingAction(chatId, tenantId, toolCallId);
        requirePendingConfirmable(pendingAction);
        String processInstanceId = newProcessInstanceId();
        acquireLease(chatId, toolCallId, processInstanceId);
        try {
            confirmationStateMachine.commitCanceled(chatId, tenantId, toolCallId);
            return new ConfirmActionResult("CANCELED", Map.of("state", "canceled"));
        } finally {
            confirmationLeaseService.release(chatId, toolCallId, processInstanceId);
        }
    }

    public ConfirmActionResult cancelByChatMessageId(UUID chatMessageId) {
        UUID tenantId = TenantContext.currentTenantUuid();
        PendingAction pendingAction =
                confirmationStateMachine.loadPendingActionByChatMessageId(tenantId, chatMessageId);
        return cancel(pendingAction.chatId(), pendingAction.toolCallId());
    }

    private void requirePendingConfirmable(PendingAction pendingAction) {
        if (!"PENDING".equals(pendingAction.state())) {
            throw new IllegalStateException("pending action is not pending");
        }
        if (pendingAction.toolCategory() == ToolCategory.READ) {
            throw new IllegalArgumentException("read tools do not require confirmation");
        }
    }

    private void acquireLease(UUID chatId, String toolCallId, String processInstanceId) {
        boolean leaseAcquired =
                confirmationLeaseService.tryAcquire(chatId, toolCallId, processInstanceId);
        if (!leaseAcquired) {
            throw new ConfirmationLeaseConflictException();
        }
    }

    private static String newProcessInstanceId() {
        return "confirm-" + UUID.randomUUID();
    }

    private static Map<String, Object> effectiveInput(
            Reservation reservation, Map<String, Object> contentOverride) {
        LinkedHashMap<String, Object> mergedInput = new LinkedHashMap<>(reservation.inputJson());
        mergedInput.putAll(safeOverride(contentOverride));
        return Map.copyOf(mergedInput);
    }

    private static Map<String, Object> safeOverride(Map<String, Object> contentOverride) {
        return contentOverride == null ? Map.of() : Map.copyOf(contentOverride);
    }

    public record ConfirmActionResult(String state, Map<String, Object> resultSummary) {}
}
