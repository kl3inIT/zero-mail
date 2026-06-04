package com.zeromail.core.chat.usecases.tools;

import com.zeromail.core.chat.confirm.send.AssistantWriteCommand;
import com.zeromail.core.chat.confirm.send.AssistantWriteExecutor.WriteToolHandler;
import com.zeromail.core.chat.confirm.send.AssistantWriteExecutor.WriteToolResult;
import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.chat.usecases.AssistantKnowledgeService;
import com.zeromail.core.chat.usecases.AssistantPersonalInstructionsService;
import com.zeromail.core.rules.projection.RuleStatusProjection;
import com.zeromail.core.rules.usecases.RuleCompileCommand;
import com.zeromail.core.rules.usecases.RuleCompileResult;
import com.zeromail.core.rules.usecases.RuleCompilerService;
import com.zeromail.core.rules.usecases.RuleCreateCommand;
import com.zeromail.core.rules.usecases.RuleManagementService;
import com.zeromail.core.triage.usecases.SenderSafetyEntryService;
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
            RuleCompilerService ruleCompilerService,
            AssistantKnowledgeService assistantKnowledgeService,
            AssistantPersonalInstructionsService assistantPersonalInstructionsService,
            SenderSafetyEntryService senderSafetyEntryService,
            TriageGmailWriter triageGmailWriter) {
        EnumMap<ChatToolName, WriteToolHandler> handlerMap = new EnumMap<>(ChatToolName.class);
        handlerMap.put(
                ChatToolName.CREATE_RULE,
                command -> createRule(ruleManagementService, ruleCompilerService, command));
        handlerMap.put(
                ChatToolName.DELETE_RULE, command -> deleteRule(ruleManagementService, command));
        handlerMap.put(
                ChatToolName.REMOVE_SENDER_FROM_SAFETY_NET,
                command -> removeSenderFromSafetyNet(senderSafetyEntryService, command));
        handlerMap.put(
                ChatToolName.BULK_ARCHIVE, command -> bulkArchive(triageGmailWriter, command));
        handlerMap.put(
                ChatToolName.SAVE_MEMORY,
                command -> saveMemory(assistantKnowledgeService, command));
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
            RuleManagementService ruleManagementService,
            RuleCompilerService ruleCompilerService,
            AssistantWriteCommand command) {
        String effectiveSourceText = WriteToolArguments.text(command.inputJson(), "sourceText");
        String compiledSourceText =
                WriteToolArguments.optionalText(command.inputJson(), "compiledSourceText");
        String overriddenDisplayName =
                WriteToolArguments.optionalText(command.inputJson(), "displayName");
        String compiledDisplayName =
                WriteToolArguments.optionalText(command.inputJson(), "compiledDisplayName");

        // The user can edit the rule name and the natural-language "When" text on the preview card
        // before confirming. If the text changed (or was never successfully compiled), recompile so
        // the matcher/action match what the user now sees -- backend never re-derives them from the
        // raw text itself (CLAUDE.md). A name-only edit reuses the stored compile.
        boolean ruleTextChanged =
                compiledSourceText == null || !compiledSourceText.equals(effectiveSourceText);
        RuleCompileResult compileResult;
        String displayName;
        if (ruleTextChanged) {
            compileResult =
                    ruleCompilerService.compile(
                            new RuleCompileCommand(command.tenantId(), effectiveSourceText));
            // Recompile of the edited text can legally come back as
            // CLARIFICATION_REQUIRED/INVALID. Fail fast with a clear message rather than
            // letting RuleCreateCommand throw its generic "compileResult must be compiled".
            if (!compileResult.isCompiled()) {
                throw new IllegalStateException(
                        "Edited rule text could not be compiled into a valid rule (status="
                                + compileResult.status()
                                + ")");
            }
            boolean userEditedName =
                    overriddenDisplayName != null
                            && !overriddenDisplayName.equals(compiledDisplayName);
            displayName = userEditedName ? overriddenDisplayName : compileResult.displayName();
        } else {
            compileResult = WriteReversibleToolHandlers.ruleCompileResult(command);
            // A name-only edit keeps the stored compile; fall back to its display name when the
            // user did not provide an override so RuleCreateCommand never sees a blank name.
            displayName =
                    overriddenDisplayName != null
                            ? overriddenDisplayName
                            : compileResult.displayName();
        }
        RuleStatusProjection projection =
                ruleManagementService.create(
                        new RuleCreateCommand(
                                command.tenantId(),
                                displayName,
                                effectiveSourceText,
                                compileResult));
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
            SenderSafetyEntryService senderSafetyEntryService, AssistantWriteCommand command) {
        SenderSafetyEntryService.SenderSafetyRemoval removal =
                senderSafetyEntryService.removeProtectedSender(
                        command.tenantId(),
                        WriteToolArguments.text(command.inputJson(), "senderEmail"));
        return WriteReversibleToolHandlers.result(
                "sender_email_hash", removal.senderEmailHash(), "removed", removal.removed());
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
            AssistantKnowledgeService assistantKnowledgeService, AssistantWriteCommand command) {
        java.util.UUID knowledgeSnippetId =
                assistantKnowledgeService.appendChatMemory(
                        command.tenantId(),
                        WriteToolArguments.text(command.inputJson(), "content"));
        return WriteReversibleToolHandlers.result(
                "knowledge_snippet_id", knowledgeSnippetId.toString());
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
