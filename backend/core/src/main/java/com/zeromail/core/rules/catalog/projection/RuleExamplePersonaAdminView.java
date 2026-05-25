package com.zeromail.core.rules.catalog.projection;

import java.util.List;
import java.util.UUID;

public record RuleExamplePersonaAdminView(
        UUID personaId,
        String personaKey,
        String displayNameEn,
        String displayNameVi,
        String icon,
        int displayOrder,
        boolean enabled,
        List<RuleExamplePromptAdminView> prompts) {}
