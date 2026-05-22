package com.zeromail.core.rules.catalog.projection;

import java.util.List;
import java.util.UUID;

public record RuleExamplePersonaView(
        UUID personaId,
        String personaKey,
        String displayName,
        String icon,
        int displayOrder,
        List<RuleExamplePromptView> prompts) {}
