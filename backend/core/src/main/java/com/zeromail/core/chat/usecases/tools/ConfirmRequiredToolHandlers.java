package com.zeromail.core.chat.usecases.tools;

import com.zeromail.core.chat.confirm.send.AssistantWriteCommand;
import com.zeromail.core.chat.confirm.send.AssistantWriteExecutor.WriteToolHandler;
import com.zeromail.core.chat.confirm.send.AssistantWriteExecutor.WriteToolResult;
import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.chat.usecases.AssistantMemoryService;
import com.zeromail.core.chat.usecases.AssistantPersonalInstructionsService;
import com.zeromail.core.rules.projection.RuleStatusProjection;
import com.zeromail.core.rules.usecases.RuleCreateCommand;
import com.zeromail.core.rules.usecases.RuleManagementService;
import com.zeromail.core.triage.persistence.TenantProtectedSenderObservationRepository;
import com.zeromail.core.triage.usecases.SenderEmailCanonicalizer;
import com.zeromail.core.triage.usecases.TriageGmailWriter;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ConfirmRequiredToolHandlers {

    private final Map<ChatToolName, WriteToolHandler> handlers;

    public ConfirmRequiredToolHandlers(
            RuleManagementService ruleManagementService,
            AssistantMemoryService assistantMemoryService,
            AssistantPersonalInstructionsService assistantPersonalInstructionsService,
            TenantProtectedSenderObservationRepository protectedSenderObservationRepository,
            SenderEmailCanonicalizer senderEmailCanonicalizer,
            TriageGmailWriter triageGmailWriter) {
        EnumMap<ChatToolName, WriteToolHandler> handlerMap = new EnumMap<>(ChatToolName.class);
        handlerMap.put(
                ChatToolName.CREATE_RULE, command -> createRule(ruleManagementService, command));
        handlerMap.put(
                ChatToolName.DELETE_RULE, command -> deleteRule(ruleManagementService, command));
        handlerMap.put(
                ChatToolName.REMOVE_SENDER_FROM_SAFETY_NET,
                command ->
                        removeSenderFromSafetyNet(
                                protectedSenderObservationRepository,
                                senderEmailCanonicalizer,
                                command));
        handlerMap.put(
                ChatToolName.BULK_ARCHIVE, command -> bulkArchive(triageGmailWriter, command));
        handlerMap.put(
                ChatToolName.SAVE_MEMORY, command -> saveMemory(assistantMemoryService, command));
        handlerMap.put(
                ChatToolName.UPDATE_PERSONAL_INSTRUCTIONS,
                command ->
                        updatePersonalInstructions(assistantPersonalInstructionsService, command));
        this.handlers = Map.copyOf(handlerMap);
    }

    public Map<ChatToolName, WriteToolHandler> handlers() {
        return handlers;
    }

    private static WriteToolResult createRule(
            RuleManagementService ruleManagementService, AssistantWriteCommand command) {
        RuleStatusProjection projection =
                ruleManagementService.create(
                        new RuleCreateCommand(
                                command.tenantId(),
                                WriteToolArguments.text(command.inputJson(), "displayName"),
                                WriteToolArguments.text(command.inputJson(), "sourceText"),
                                WriteReversibleToolHandlers.ruleCompileResult(command)));
        return WriteReversibleToolHandlers.result(
                "rule_id", projection.ruleId().toString(), "enabled", projection.enabled());
    }

    private static WriteToolResult deleteRule(
            RuleManagementService ruleManagementService, AssistantWriteCommand command) {
        java.util.UUID ruleId = WriteToolArguments.uuid(command.inputJson(), "ruleId");
        ruleManagementService.delete(command.tenantId(), ruleId);
        return WriteReversibleToolHandlers.result("rule_id", ruleId.toString(), "deleted", true);
    }

    private static WriteToolResult removeSenderFromSafetyNet(
            TenantProtectedSenderObservationRepository protectedSenderObservationRepository,
            SenderEmailCanonicalizer senderEmailCanonicalizer,
            AssistantWriteCommand command) {
        String canonicalSenderEmail =
                senderEmailCanonicalizer.canonicalize(
                        WriteToolArguments.text(command.inputJson(), "senderEmail"));
        protectedSenderObservationRepository
                .findByTenantIdAndSenderEmail(command.tenantId(), canonicalSenderEmail)
                .ifPresent(protectedSenderObservationRepository::delete);
        return WriteReversibleToolHandlers.result(
                "sender_email_hash",
                senderEmailCanonicalizer.redisCacheKeyComponent(canonicalSenderEmail),
                "removed",
                true);
    }

    private static WriteToolResult bulkArchive(
            TriageGmailWriter triageGmailWriter, AssistantWriteCommand command) {
        try {
            java.util.List<String> threadIds =
                    WriteToolArguments.textList(command.inputJson(), "threadIds");
            for (String threadId : threadIds) {
                triageGmailWriter.removeLabel(command.tenantId(), threadId, "INBOX");
            }
            return WriteReversibleToolHandlers.result("thread_count", threadIds.size());
        } catch (IOException gmailWriteFailure) {
            throw new IllegalStateException("Gmail bulk archive failed", gmailWriteFailure);
        }
    }

    private static WriteToolResult saveMemory(
            AssistantMemoryService assistantMemoryService, AssistantWriteCommand command) {
        java.util.UUID memoryId =
                assistantMemoryService.save(
                        command.tenantId(),
                        WriteToolArguments.text(command.inputJson(), "content"));
        return WriteReversibleToolHandlers.result("memory_id", memoryId.toString());
    }

    private static WriteToolResult updatePersonalInstructions(
            AssistantPersonalInstructionsService assistantPersonalInstructionsService,
            AssistantWriteCommand command) {
        AssistantPersonalInstructionsService.InstructionUpdateResult updateResult =
                assistantPersonalInstructionsService.overwrite(
                        command.tenantId(),
                        WriteToolArguments.text(command.inputJson(), "personalInstructions"));
        return new WriteToolResult(updateResult.toSummary());
    }
}
