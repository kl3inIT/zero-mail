package com.zeromail.core.chat.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum ChatToolName implements IdentifiedEnum {
    SEARCH_INBOX("searchInbox", ToolCategory.READ),
    GET_MESSAGE("getMessage", ToolCategory.READ),
    LIST_LABELS("listLabels", ToolCategory.READ),
    GET_THREAD("getThread", ToolCategory.READ),
    GET_RULE("getRule", ToolCategory.READ),
    LIST_RULES("listRules", ToolCategory.READ),
    GET_SENDER_SAFETY_ENTRY("getSenderSafetyEntry", ToolCategory.READ),
    SEARCH_MEMORIES("searchMemories", ToolCategory.READ),

    APPLY_LABEL("applyLabel", ToolCategory.WRITE_REVERSIBLE),
    REMOVE_LABEL("removeLabel", ToolCategory.WRITE_REVERSIBLE),
    ARCHIVE_THREAD("archiveThread", ToolCategory.WRITE_REVERSIBLE),
    UPDATE_RULE("updateRule", ToolCategory.WRITE_REVERSIBLE),
    DISABLE_RULE("disableRule", ToolCategory.WRITE_REVERSIBLE),
    SAVE_DRAFT("saveDraft", ToolCategory.WRITE_REVERSIBLE),

    CREATE_RULE("createRule", ToolCategory.CONFIRM_REQUIRED),
    DELETE_RULE("deleteRule", ToolCategory.CONFIRM_REQUIRED),
    REMOVE_SENDER_FROM_SAFETY_NET("removeSenderFromSafetyNet", ToolCategory.CONFIRM_REQUIRED),
    BULK_ARCHIVE("bulkArchive", ToolCategory.CONFIRM_REQUIRED),
    SAVE_MEMORY("saveMemory", ToolCategory.CONFIRM_REQUIRED),
    UPDATE_PERSONAL_INSTRUCTIONS("updatePersonalInstructions", ToolCategory.CONFIRM_REQUIRED),

    SEND_EMAIL("sendEmail", ToolCategory.CONFIRMED_SEND),
    REPLY_EMAIL("replyEmail", ToolCategory.CONFIRMED_SEND),
    FORWARD_EMAIL("forwardEmail", ToolCategory.CONFIRMED_SEND);

    private final String id;
    private final ToolCategory category;

    ChatToolName(String id, ToolCategory category) {
        this.id = id;
        this.category = category;
    }

    @Override
    public String id() {
        return id;
    }

    public ToolCategory category() {
        return category;
    }

    public static ChatToolName fromId(String id) {
        return Stream.of(values())
                .filter(chatToolName -> chatToolName.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown ChatToolName id: " + id));
    }

    public static List<ChatToolName> byCategory(ToolCategory category) {
        return Stream.of(values())
                .filter(chatToolName -> chatToolName.category == category)
                .toList();
    }
}
