package com.zeromail.core.llm.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.llm.domain.Action;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolCallResultCompatibilityTest {

    @Test
    void keeps_existing_constructor_action_and_argument_behavior() {
        Map<String, Object> mutableArguments = new HashMap<>();
        mutableArguments.put("value", "Receipts");

        ToolCallResult result = new ToolCallResult(Action.LABEL, mutableArguments);
        mutableArguments.put("value", "Mutated");

        assertThat(result.action()).isEqualTo(Action.LABEL);
        assertThat(result.args()).containsEntry("value", "Receipts");
    }

    @Test
    void action_enum_membership_covers_rule_compile_contract() {
        assertThat(Arrays.stream(Action.values()).map(Action::functionName))
                .containsExactlyInAnyOrder(
                        "label",
                        "archive",
                        "save_draft",
                        "mark_read",
                        "star",
                        "add_to_digest",
                        "mark_spam",
                        "send_reply",
                        "forward_email",
                        "send_email");
    }
}
