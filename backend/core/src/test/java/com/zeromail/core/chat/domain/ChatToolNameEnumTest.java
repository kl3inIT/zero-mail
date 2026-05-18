package com.zeromail.core.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ChatToolNameEnumTest {

    @Test
    void lists_exact_phase_seven_tool_contract() {
        assertThat(ChatToolName.values()).hasSize(24);
        assertThat(ChatToolName.byCategory(ToolCategory.READ)).hasSize(8);
        assertThat(ChatToolName.byCategory(ToolCategory.WRITE_REVERSIBLE)).hasSize(7);
        assertThat(ChatToolName.byCategory(ToolCategory.CONFIRM_REQUIRED)).hasSize(6);
        assertThat(ChatToolName.byCategory(ToolCategory.CONFIRMED_SEND)).hasSize(3);

        assertThat(ChatToolName.fromId("createRule").category())
                .isEqualTo(ToolCategory.CONFIRM_REQUIRED);
        assertThat(toolIds())
                .containsExactly(
                        "searchInbox",
                        "getMessage",
                        "listLabels",
                        "getThread",
                        "getRule",
                        "listRules",
                        "getSenderSafetyEntry",
                        "searchMemories",
                        "applyLabel",
                        "removeLabel",
                        "archiveThread",
                        "updateRule",
                        "disableRule",
                        "saveDraft",
                        "addToKnowledgeBase",
                        "createRule",
                        "deleteRule",
                        "removeSenderFromSafetyNet",
                        "bulkArchive",
                        "saveMemory",
                        "updatePersonalInstructions",
                        "sendEmail",
                        "replyEmail",
                        "forwardEmail");
    }

    @Test
    void excludes_deferred_or_renamed_tool_aliases() {
        Set<String> toolIds =
                Stream.of(ChatToolName.values())
                        .map(ChatToolName::id)
                        .collect(Collectors.toUnmodifiableSet());

        assertThat(toolIds)
                .doesNotContain(
                        "readEmail", "unarchiveThread", "getInboxStats", "createOrGetLabel");
    }

    private static String[] toolIds() {
        return Stream.of(ChatToolName.values()).map(ChatToolName::id).toArray(String[]::new);
    }
}
