package com.zeromail.core.chat.usecases;

import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.chat.domain.ToolCategory;
import com.zeromail.core.chat.domain.sendaction.ForwardEmailToolArgs;
import com.zeromail.core.chat.domain.sendaction.ReplyEmailToolArgs;
import com.zeromail.core.chat.domain.sendaction.SendEmailToolArgs;
import com.zeromail.core.shared.privacy.Sensitive;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ChatToolCatalog {

    private static final Map<ToolCategory, Integer> EXPECTED_PARTITION =
            Map.of(
                    ToolCategory.READ, 8,
                    ToolCategory.WRITE_REVERSIBLE, 7,
                    ToolCategory.CONFIRM_REQUIRED, 6,
                    ToolCategory.CONFIRMED_SEND, 3);

    private final List<ToolDefinition> toolDefinitions;

    public ChatToolCatalog() {
        this(defaultDefinitions());
    }

    public ChatToolCatalog(List<ToolDefinition> toolDefinitions) {
        this.toolDefinitions = toolDefinitions == null ? List.of() : List.copyOf(toolDefinitions);
        validate();
    }

    public List<ToolDefinition> toolDefinitions() {
        return toolDefinitions;
    }

    @SuppressWarnings("unused")
    public Optional<ToolDefinition> byName(ChatToolName name) {
        return toolDefinitions.stream()
                .filter(toolDefinition -> toolDefinition.name() == name)
                .findFirst();
    }

    @SuppressWarnings("unused")
    public List<ToolDefinition> byCategory(ToolCategory category) {
        return toolDefinitions.stream()
                .filter(toolDefinition -> toolDefinition.category() == category)
                .toList();
    }

    public void validate() {
        if (toolDefinitions.size() != ChatToolName.values().length) {
            throw new IllegalStateException("Chat tool catalog must contain exactly 24 tools");
        }
        EnumSet<ChatToolName> seenToolNames = EnumSet.noneOf(ChatToolName.class);
        EnumMap<ToolCategory, Integer> partition = new EnumMap<>(ToolCategory.class);
        for (ToolDefinition toolDefinition : toolDefinitions) {
            if (toolDefinition.name() == null) {
                throw new IllegalStateException("Chat tool definition missing name");
            }
            if (!seenToolNames.add(toolDefinition.name())) {
                throw new IllegalStateException(
                        "Duplicate chat tool: " + toolDefinition.name().id());
            }
            if (toolDefinition.description() == null || toolDefinition.description().isBlank()) {
                throw new IllegalStateException(
                        "Chat tool description missing for " + toolDefinition.name().id());
            }
            if (toolDefinition.argsRecordClass() == null) {
                throw new IllegalStateException(
                        "Chat tool args type missing for " + toolDefinition.name().id());
            }
            if (toolDefinition.category() != toolDefinition.name().category()) {
                throw new IllegalStateException(
                        "Chat tool category mismatch for " + toolDefinition.name().id());
            }
            partition.merge(toolDefinition.category(), 1, Integer::sum);
        }
        EnumSet<ChatToolName> missingToolNames = EnumSet.allOf(ChatToolName.class);
        missingToolNames.removeAll(seenToolNames);
        if (!missingToolNames.isEmpty()) {
            throw new IllegalStateException("Missing chat tools: " + missingToolNames);
        }
        EXPECTED_PARTITION.forEach(
                (toolCategory, expectedCount) -> {
                    int actualCount = partition.getOrDefault(toolCategory, 0);
                    if (actualCount != expectedCount) {
                        throw new IllegalStateException(
                                "Chat tool category "
                                        + toolCategory
                                        + " expected "
                                        + expectedCount
                                        + " but found "
                                        + actualCount);
                    }
                });
    }

    private static List<ToolDefinition> defaultDefinitions() {
        return List.of(
                tool(
                        ChatToolName.SEARCH_INBOX,
                        "Search Gmail messages for this tenant",
                        SearchInboxArgs.class),
                tool(
                        ChatToolName.GET_MESSAGE,
                        "Fetch one Gmail message for in-memory reasoning",
                        GetMessageArgs.class),
                tool(
                        ChatToolName.LIST_LABELS,
                        "List Gmail labels available to the tenant",
                        ListLabelsArgs.class),
                tool(
                        ChatToolName.GET_THREAD,
                        "Fetch thread metadata and participants",
                        GetThreadArgs.class),
                tool(ChatToolName.GET_RULE, "Fetch one saved Zero Mail rule", GetRuleArgs.class),
                tool(ChatToolName.LIST_RULES, "List saved Zero Mail rules", ListRulesArgs.class),
                tool(
                        ChatToolName.GET_SENDER_SAFETY_ENTRY,
                        "Fetch sender safety-net state",
                        GetSenderSafetyEntryArgs.class),
                tool(
                        ChatToolName.SEARCH_MEMORIES,
                        "Search assistant memories",
                        SearchMemoriesArgs.class),
                tool(ChatToolName.APPLY_LABEL, "Apply a Gmail label", ApplyLabelArgs.class),
                tool(ChatToolName.REMOVE_LABEL, "Remove a Gmail label", RemoveLabelArgs.class),
                tool(
                        ChatToolName.ARCHIVE_THREAD,
                        "Archive a Gmail thread",
                        ArchiveThreadArgs.class),
                tool(ChatToolName.UPDATE_RULE, "Update an existing rule", UpdateRuleArgs.class),
                tool(ChatToolName.DISABLE_RULE, "Disable an existing rule", DisableRuleArgs.class),
                tool(
                        ChatToolName.SAVE_DRAFT,
                        "Save a Gmail draft without sending",
                        SaveDraftArgs.class),
                tool(
                        ChatToolName.ADD_TO_KNOWLEDGE_BASE,
                        "Add assistant knowledge snippet",
                        AddToKnowledgeBaseArgs.class),
                tool(
                        ChatToolName.CREATE_RULE,
                        "Create a rule after explicit confirmation",
                        CreateRuleArgs.class),
                tool(
                        ChatToolName.DELETE_RULE,
                        "Delete a rule after explicit confirmation",
                        DeleteRuleArgs.class),
                tool(
                        ChatToolName.REMOVE_SENDER_FROM_SAFETY_NET,
                        "Remove sender safety-net entry",
                        RemoveSenderFromSafetyNetArgs.class),
                tool(
                        ChatToolName.BULK_ARCHIVE,
                        "Bulk archive after explicit confirmation",
                        BulkArchiveArgs.class),
                tool(
                        ChatToolName.SAVE_MEMORY,
                        "Save assistant memory after preview",
                        SaveMemoryArgs.class),
                tool(
                        ChatToolName.UPDATE_PERSONAL_INSTRUCTIONS,
                        "Update personal instructions after preview",
                        UpdatePersonalInstructionsArgs.class),
                tool(
                        ChatToolName.SEND_EMAIL,
                        "Compose a NEW email to a recipient (not a reply within an existing"
                                + " thread). Call this whenever the user expresses intent to send"
                                + " a fresh email, even if some fields contain placeholders --"
                                + " the user will edit any field in the preview card before"
                                + " confirming. Do NOT describe the email in plain assistant text"
                                + " instead of calling this tool.",
                        SendEmailToolArgs.class),
                tool(
                        ChatToolName.REPLY_EMAIL,
                        "Reply within an existing email thread. Requires a messageId from"
                                + " searchInbox/getMessage. Call this when the user wants to"
                                + " respond to a specific message.",
                        ReplyEmailToolArgs.class),
                tool(
                        ChatToolName.FORWARD_EMAIL,
                        "Forward an existing email message to new recipients. Requires the"
                                + " messageId of the email to forward.",
                        ForwardEmailToolArgs.class));
    }

    private static ToolDefinition tool(
            ChatToolName name, String description, Class<?> argsRecordClass) {
        return new ToolDefinition(name, description, argsRecordClass, name.category());
    }

    public record ToolDefinition(
            ChatToolName name,
            String description,
            Class<?> argsRecordClass,
            ToolCategory category) {}

    public record SearchInboxArgs(String query, Integer maxResults) {}

    public record GetMessageArgs(String messageId) {}

    public record ListLabelsArgs() {}

    public record GetThreadArgs(String threadId) {}

    public record GetRuleArgs(UUID ruleId) {}

    public record ListRulesArgs() {}

    public record GetSenderSafetyEntryArgs(String senderEmail) {}

    public record SearchMemoriesArgs(String query) {}

    public record ApplyLabelArgs(String targetId, String labelId) {}

    public record RemoveLabelArgs(String targetId, String labelId) {}

    public record ArchiveThreadArgs(String threadId) {}

    public record UpdateRuleArgs(UUID ruleId, Map<String, Object> changes) {}

    public record DisableRuleArgs(UUID ruleId) {}

    public record SaveDraftArgs(String to, String subject, Sensitive<String> body) {}

    public record AddToKnowledgeBaseArgs(String content) {}

    public record CreateRuleArgs(String sourceText) {}

    public record DeleteRuleArgs(UUID ruleId) {}

    public record RemoveSenderFromSafetyNetArgs(String senderEmail) {}

    public record BulkArchiveArgs(List<String> threadIds) {}

    public record SaveMemoryArgs(String content) {}

    public record UpdatePersonalInstructionsArgs(String personalInstructions) {}
}
