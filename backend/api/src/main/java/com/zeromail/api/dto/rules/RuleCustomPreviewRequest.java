package com.zeromail.api.dto.rules;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record RuleCustomPreviewRequest(
        @Size(max = 500) String subject,
        @Size(max = 50_000) String body,
        @Size(max = 64) List<UUID> ruleIds) {}
