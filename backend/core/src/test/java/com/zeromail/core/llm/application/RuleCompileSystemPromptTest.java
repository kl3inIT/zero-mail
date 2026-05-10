package com.zeromail.core.llm.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RuleCompileSystemPromptTest {

  @Test
  void rule_compile_prompt_contains_required_guardrails() {
    assertThat(SystemPrompts.RULE_COMPILE_SYSTEM_PROMPT)
        .contains("untrusted data")
        .contains("exactly one rule_compile tool call")
        .contains("rules.v1")
        .contains("sender email or domain")
        .contains("SEMANTIC_INTENT as a deferred marker only")
        .contains("label, archive, and save_draft")
        .contains("clarificationRequired=true")
        .contains("never logged or persisted");
  }
}
