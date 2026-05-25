package com.zeromail.core.rules.catalog.projection;

import java.util.UUID;

public record RuleExamplePromptAdminView(
        UUID promptId,
        String exampleTextEn,
        String exampleTextVi,
        int displayOrder,
        boolean enabled) {}
