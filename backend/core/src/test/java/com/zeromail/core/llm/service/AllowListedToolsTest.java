package com.zeromail.core.llm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.llm.domain.Action;
import com.zeromail.core.llm.usecases.LlmTool;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AllowListedToolsTest {

    @Test
    void exposes_exactly_three_allow_listed_tools() {
        Set<String> toolNames = toolNames();

        assertThat(toolNames).containsExactlyInAnyOrder("label", "archive", "save_draft");
        assertThat(toolNames)
                .allSatisfy(toolName -> assertThat(Action.fromFunctionName(toolName)).isNotNull());
    }

    @Test
    void tool_name_set_matches_action_enum() {
        Set<String> toolNames = toolNames();
        Set<String> actionFunctionNames =
                Arrays.stream(Action.values())
                        .map(Action::functionName)
                        .collect(Collectors.toUnmodifiableSet());

        assertThat(toolNames).isEqualTo(actionFunctionNames);
    }

    private Set<String> toolNames() {
        return new AllowListedTools()
                .tools().stream().map(LlmTool::name).collect(Collectors.toUnmodifiableSet());
    }
}
