package com.zeromail.api.dto.rules;

import jakarta.validation.constraints.NotBlank;

/** Per-message test request: evaluate every enabled rule (incl. LLM semantic) against one email. */
public record RuleTestMessageRequest(
        @NotBlank String gmailMessageId, @NotBlank String gmailThreadId) {}
