package com.zeromail.core.chat.confirm.send;

import com.zeromail.core.chat.confirm.ConfirmationLeaseService;
import com.zeromail.core.chat.confirm.ConfirmationStateMachine;
import com.zeromail.core.chat.confirm.ConfirmationStateMachine.WriteCommitCommand;
import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.chat.usecases.tools.ConfirmRequiredToolHandlers;
import com.zeromail.core.chat.usecases.tools.WriteReversibleToolHandlers;
import com.zeromail.core.tenant.TenantContext;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AssistantWriteExecutor {

    private final ConfirmationStateMachine confirmationStateMachine;
    private final ConfirmationLeaseService confirmationLeaseService;
    private final Map<ChatToolName, WriteToolHandler> writeToolHandlers;

    public AssistantWriteExecutor(
            ConfirmationStateMachine confirmationStateMachine,
            ConfirmationLeaseService confirmationLeaseService,
            WriteReversibleToolHandlers writeReversibleToolHandlers,
            ConfirmRequiredToolHandlers confirmRequiredToolHandlers) {
        this.confirmationStateMachine = confirmationStateMachine;
        this.confirmationLeaseService = confirmationLeaseService;
        this.writeToolHandlers = merge(writeReversibleToolHandlers, confirmRequiredToolHandlers);
    }

    public AssistantWriteResult execute(AssistantWriteCommand command) {
        String boundTenantId = TenantContext.currentOrThrow();
        if (!boundTenantId.equals(command.tenantId().toString())) {
            throw new IllegalStateException("tenant context does not match write command");
        }
        WriteToolHandler writeToolHandler = writeToolHandlers.get(command.toolName());
        if (writeToolHandler == null) {
            throw new IllegalArgumentException("No write handler for " + command.toolName().id());
        }
        WriteToolResult writeToolResult = writeToolHandler.execute(command);
        confirmationStateMachine.commitWriteReversible(
                new WriteCommitCommand(
                        command.tenantId(),
                        command.chatId(),
                        command.toolCallId(),
                        command.toolName(),
                        command.toolName().category(),
                        writeToolResult.resultSummary(),
                        command.previewSnapshot()));
        confirmationLeaseService.release(command.chatId(), command.toolCallId());
        return new AssistantWriteResult("CONFIRMED", writeToolResult.resultSummary());
    }

    private static Map<ChatToolName, WriteToolHandler> merge(
            WriteReversibleToolHandlers writeReversibleToolHandlers,
            ConfirmRequiredToolHandlers confirmRequiredToolHandlers) {
        EnumMap<ChatToolName, WriteToolHandler> handlers = new EnumMap<>(ChatToolName.class);
        handlers.putAll(writeReversibleToolHandlers.handlers());
        handlers.putAll(confirmRequiredToolHandlers.handlers());
        return Map.copyOf(handlers);
    }

    @FunctionalInterface
    public interface WriteToolHandler {
        WriteToolResult execute(AssistantWriteCommand command);
    }

    public record WriteToolResult(Map<String, Object> resultSummary) {}

    public record AssistantWriteResult(String state, Map<String, Object> resultSummary) {}
}
