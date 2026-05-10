package com.zeromail.core.llm.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.zeromail.core.llm.domain.Action;

class ToolCallResultTest {

    @Test
    void defensive_copies_args_map() {
        Map<String, Object> mutableArguments = new HashMap<>();
        mutableArguments.put("value", "Receipts");

        ToolCallResult result = new ToolCallResult(Action.LABEL, mutableArguments);
        mutableArguments.put("value", "Mutated");

        assertThat(result.args()).containsEntry("value", "Receipts");
    }
}
