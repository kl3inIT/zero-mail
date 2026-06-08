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
                    ToolCategory.WRITE_REVERSIBLE, 6,
                    ToolCategory.CONFIRM_REQUIRED, 6,
                    ToolCategory.CONFIRMED_SEND, 3);
    private static final int EXPECTED_ACTIVE_TOOL_COUNT =
            EXPECTED_PARTITION.values().stream().mapToInt(Integer::intValue).sum();

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
        if (toolDefinitions.size() != EXPECTED_ACTIVE_TOOL_COUNT) {
            throw new IllegalStateException(
                    "Chat tool catalog must contain exactly "
                            + EXPECTED_ACTIVE_TOOL_COUNT
                            + " active tools");
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
                        "Search Gmail messages for this tenant using Gmail search query"
                                + " syntax. To match a sender by brand/keyword use parentheses:"
                                + " from:(github), from:(vercel), from:(jmix). To match a full"
                                + " domain use from:domain.com (e.g. from:vercel.com). Combine"
                                + " with space (AND) or OR. Date filter: newer_than:30d."
                                + " Examples: from:(github) newer_than:30d, subject:(invoice),"
                                + " is:unread. If the first query returns 0 results, immediately"
                                + " retry with a broader form -- replace from:keyword with"
                                + " from:(keyword), drop date filters, or widen the brand match.",
                        SearchInboxArgs.class),
                tool(
                        ChatToolName.GET_MESSAGE,
                        "Fetch one Gmail message (headers + decoded plain-text body) by messageId"
                                + " for in-memory reasoning. The body is transient and never stored.",
                        GetMessageArgs.class),
                tool(
                        ChatToolName.LIST_LABELS,
                        "List Gmail labels available to the tenant",
                        ListLabelsArgs.class),
                tool(
                        ChatToolName.GET_THREAD,
                        "Fetch thread metadata for a threadId: participants and the message ids it"
                                + " contains (no bodies). Call getMessage on a message id to read"
                                + " content.",
                        GetThreadArgs.class),
                tool(ChatToolName.GET_RULE, "Fetch one saved Zero Mail rule", GetRuleArgs.class),
                tool(ChatToolName.LIST_RULES, "List saved Zero Mail rules", ListRulesArgs.class),
                tool(
                        ChatToolName.GET_SENDER_SAFETY_ENTRY,
                        "Fetch sender safety-net state",
                        GetSenderSafetyEntryArgs.class),
                tool(
                        ChatToolName.SEARCH_MEMORIES,
                        "Search saved knowledge snippets and chat-saved memories",
                        SearchMemoriesArgs.class),
                tool(
                        ChatToolName.APPLY_LABEL,
                        "Apply a Gmail label to a message. messageId is the Gmail message id (from"
                                + " searchInbox/getMessage). labelId is the label name or Gmail label"
                                + " id; an unknown name is created.",
                        ApplyLabelArgs.class),
                tool(
                        ChatToolName.REMOVE_LABEL,
                        "Remove a Gmail label from a message. messageId is the Gmail message id;"
                                + " labelId is the label name or Gmail label id.",
                        RemoveLabelArgs.class),
                tool(
                        ChatToolName.ARCHIVE_THREAD,
                        "Archive a Gmail thread (remove it from the Inbox) by threadId",
                        ArchiveThreadArgs.class),
                tool(ChatToolName.UPDATE_RULE, "Update an existing rule", UpdateRuleArgs.class),
                tool(ChatToolName.DISABLE_RULE, "Disable an existing rule", DisableRuleArgs.class),
                tool(
                        ChatToolName.SAVE_DRAFT,
                        "Save a Gmail draft without sending",
                        SaveDraftArgs.class),
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
                        "Save a user-approved knowledge snippet after preview. Use this whenever"
                                + " the user asks Zero Mail to remember something long term.",
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
                                + " confirming. Always compose a concise, specific subject line"
                                + " yourself summarising the email; leave subject empty only if"
                                + " the user explicitly asks for no subject. Do NOT describe the"
                                + " email in plain assistant text instead of calling this tool.",
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

    public record ApplyLabelArgs(String messageId, String labelId) {}

    public record RemoveLabelArgs(String messageId, String labelId) {}

    public record ArchiveThreadArgs(String threadId) {}

    public record UpdateRuleArgs(UUID ruleId, Map<String, Object> changes) {}

    public record DisableRuleArgs(UUID ruleId) {}

    public record SaveDraftArgs(
            @org.springframework.ai.tool.annotation.ToolParam(
                            description =
                                    "Recipient email address in the form name@example.com. Must be"
                                            + " a real email address, never a person's display name or"
                                            + " nickname.")
                    String to,
            String subject,
            Sensitive<String> body) {}

    public record CreateRuleArgs(String sourceText) {}

    public record DeleteRuleArgs(UUID ruleId) {}

    public record RemoveSenderFromSafetyNetArgs(String senderEmail) {}

    public record BulkArchiveArgs(List<String> threadIds) {}

    public record SaveMemoryArgs(String content) {}

    public record UpdatePersonalInstructionsArgs(String personalInstructions) {}
}
