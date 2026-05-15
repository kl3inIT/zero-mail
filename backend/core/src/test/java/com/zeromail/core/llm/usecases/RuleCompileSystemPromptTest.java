package com.zeromail.core.llm.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RuleCompileSystemPromptTest {

    @Test
    void rule_compile_prompt_contains_required_guardrails() {
        assertThat(SystemPrompts.RULE_COMPILE_SYSTEM_PROMPT)
                .contains("untrusted data")
                .contains("exactly one rule_compile tool call")
                .contains("rules.v1")
                .contains("SENDER_EMAIL")
                .contains("SENDER_DOMAIN")
                .contains("SEMANTIC_INTENT")
                .contains("Broad semantic conditions are valid review-form drafts")
                .contains("safe action intents")
                .contains("label, archive, and save_draft")
                .contains("clarificationRequired=true")
                .contains("never logged or persisted");
    }
}
