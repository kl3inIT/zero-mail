package com.zeromail.core.chat.usecases.tools;

import com.zeromail.core.chat.confirm.send.AssistantWriteCommand;
import com.zeromail.core.chat.confirm.send.AssistantWriteExecutor.WriteToolHandler;
import com.zeromail.core.chat.confirm.send.AssistantWriteExecutor.WriteToolResult;
import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.chat.usecases.AssistantKnowledgeService;
import com.zeromail.core.rules.domain.RuleLanguage;
import com.zeromail.core.rules.domain.RuleSchemaVersion;
import com.zeromail.core.rules.projection.RuleStatusProjection;
import com.zeromail.core.rules.usecases.RuleCompileResult;
import com.zeromail.core.rules.usecases.RuleManagementService;
import com.zeromail.core.rules.usecases.RuleUpdateCommand;
import com.zeromail.core.triage.domain.ReplyHeaders;
import com.zeromail.core.triage.usecases.TriageGmailWriter;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WriteReversibleToolHandlers {

    private static final String INBOX_LABEL_ID = "INBOX";

    private final Map<ChatToolName, WriteToolHandler> handlers;

    public WriteReversibleToolHandlers(
            TriageGmailWriter triageGmailWriter,
            RuleManagementService ruleManagementService,
            AssistantKnowledgeService assistantKnowledgeService) {
        EnumMap<ChatToolName, WriteToolHandler> handlerMap = new EnumMap<>(ChatToolName.class);
        handlerMap.put(ChatToolName.APPLY_LABEL, command -> applyLabel(triageGmailWriter, command));
        handlerMap.put(
                ChatToolName.REMOVE_LABEL, command -> removeLabel(triageGmailWriter, command));
        handlerMap.put(
                ChatToolName.ARCHIVE_THREAD, command -> archiveThread(triageGmailWriter, command));
        handlerMap.put(
                ChatToolName.UPDATE_RULE, command -> updateRule(ruleManagementService, command));
        handlerMap.put(
                ChatToolName.DISABLE_RULE, command -> disableRule(ruleManagementService, command));
        handlerMap.put(ChatToolName.SAVE_DRAFT, command -> saveDraft(triageGmailWriter, command));
        handlerMap.put(
                ChatToolName.ADD_TO_KNOWLEDGE_BASE,
                command -> addToKnowledgeBase(assistantKnowledgeService, command));
        this.handlers = Map.copyOf(handlerMap);
    }

    public Map<ChatToolName, WriteToolHandler> handlers() {
        return handlers;
    }

    private static WriteToolResult applyLabel(
            TriageGmailWriter triageGmailWriter, AssistantWriteCommand command) {
        try {
            String messageId = WriteToolArguments.text(command.inputJson(), "messageId");
            String labelId = WriteToolArguments.text(command.inputJson(), "labelId");
            String resolvedLabelId =
                    triageGmailWriter.applyLabel(command.tenantId(), messageId, labelId);
            return result("message_id", messageId, "label_id", resolvedLabelId);
        } catch (IOException gmailWriteFailure) {
            throw new IllegalStateException("Gmail label write failed", gmailWriteFailure);
        }
    }

    private static WriteToolResult removeLabel(
            TriageGmailWriter triageGmailWriter, AssistantWriteCommand command) {
        try {
            String messageId = WriteToolArguments.text(command.inputJson(), "messageId");
            String labelId = WriteToolArguments.text(command.inputJson(), "labelId");
            triageGmailWriter.removeLabel(command.tenantId(), messageId, labelId);
            return result("message_id", messageId, "label_id", labelId);
        } catch (IOException gmailWriteFailure) {
            throw new IllegalStateException("Gmail label removal failed", gmailWriteFailure);
        }
    }

    private static WriteToolResult archiveThread(
            TriageGmailWriter triageGmailWriter, AssistantWriteCommand command) {
        try {
            String threadId = WriteToolArguments.text(command.inputJson(), "threadId");
            triageGmailWriter.removeLabel(command.tenantId(), threadId, INBOX_LABEL_ID);
            return result("thread_id", threadId, "label_id", INBOX_LABEL_ID);
        } catch (IOException gmailWriteFailure) {
            throw new IllegalStateException("Gmail archive failed", gmailWriteFailure);
        }
    }

    private static WriteToolResult updateRule(
            RuleManagementService ruleManagementService, AssistantWriteCommand command) {
        RuleCompileResult compileResult = ruleCompileResult(command);
        RuleStatusProjection projection =
                ruleManagementService.update(
                        new RuleUpdateCommand(
                                command.tenantId(),
                                WriteToolArguments.uuid(command.inputJson(), "ruleId"),
                                WriteToolArguments.text(command.inputJson(), "displayName"),
                                WriteToolArguments.text(command.inputJson(), "sourceText"),
                                compileResult,
                                WriteToolArguments.integer(
                                        command.inputJson(), "expectedEntityVersion", 0)));
        return result("rule_id", projection.ruleId().toString(), "enabled", projection.enabled());
    }

    private static WriteToolResult disableRule(
            RuleManagementService ruleManagementService, AssistantWriteCommand command) {
        RuleStatusProjection projection =
                ruleManagementService.disable(
                        command.tenantId(), WriteToolArguments.uuid(command.inputJson(), "ruleId"));
        return result("rule_id", projection.ruleId().toString(), "enabled", projection.enabled());
    }

    private static WriteToolResult saveDraft(
            TriageGmailWriter triageGmailWriter, AssistantWriteCommand command) {
        try {
            String gmailThreadId =
                    WriteToolArguments.optionalText(command.inputJson(), "gmailThreadId");
            if (gmailThreadId == null) {
                gmailThreadId = WriteToolArguments.text(command.inputJson(), "threadId");
            }
            String to = WriteToolArguments.text(command.inputJson(), "to");
            String body = WriteToolArguments.text(command.inputJson(), "body");
            String subject = WriteToolArguments.optionalText(command.inputJson(), "subject");
            ReplyHeaders replyHeaders = ReplyHeaders.of(null, null, subject, to, gmailThreadId);
            String draftId =
                    triageGmailWriter.saveDraft(
                            command.tenantId(), replyHeaders, body, gmailThreadId);
            return result("draft_id", draftId, "gmail_thread_id", gmailThreadId);
        } catch (IOException gmailWriteFailure) {
            throw new IllegalStateException("Gmail draft save failed", gmailWriteFailure);
        }
    }

    private static WriteToolResult addToKnowledgeBase(
            AssistantKnowledgeService assistantKnowledgeService, AssistantWriteCommand command) {
        String title = WriteToolArguments.text(command.inputJson(), "title");
        String content = WriteToolArguments.text(command.inputJson(), "content");
        java.util.UUID snippetId =
                assistantKnowledgeService.append(command.tenantId(), title, content);
        return result("knowledge_snippet_id", snippetId.toString());
    }

    static RuleCompileResult ruleCompileResult(AssistantWriteCommand command) {
        String displayName = WriteToolArguments.text(command.inputJson(), "displayName");
        String matcherAst = WriteToolArguments.text(command.inputJson(), "matcherAst");
        String actionIntents = WriteToolArguments.text(command.inputJson(), "actionIntents");
        return RuleCompileResult.compiled(
                RuleLanguage.EN,
                displayName,
                RuleSchemaVersion.RULES_V1,
                matcherAst,
                actionIntents);
    }

    static WriteToolResult result(Object... keysAndValues) {
        if ((keysAndValues.length & 1) != 0) {
            throw new IllegalArgumentException("keysAndValues must contain key/value pairs");
        }
        java.util.LinkedHashMap<String, Object> resultSummary = new java.util.LinkedHashMap<>();
        for (int valueIndex = 0; valueIndex < keysAndValues.length; valueIndex += 2) {
            resultSummary.put(
                    String.valueOf(keysAndValues[valueIndex]), keysAndValues[valueIndex + 1]);
        }
        return new WriteToolResult(Map.copyOf(resultSummary));
    }
}
