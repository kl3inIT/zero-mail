package com.zeromail.core.rules.catalog.projection;

import java.util.UUID;

public record RuleExamplePromptView(
        UUID promptId, String sourceRef, String exampleText, int displayOrder) {}
