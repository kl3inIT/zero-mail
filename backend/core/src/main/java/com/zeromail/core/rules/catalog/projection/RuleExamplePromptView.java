package com.zeromail.core.rules.catalog.projection;

import java.util.UUID;

public record RuleExamplePromptView(UUID promptId, String exampleText, int displayOrder) {}
