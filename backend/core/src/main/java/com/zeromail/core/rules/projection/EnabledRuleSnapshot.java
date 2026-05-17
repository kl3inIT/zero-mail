package com.zeromail.core.rules.projection;

import java.util.UUID;

/**
 * Read-side snapshot of an enabled rule, exposing only the raw fields downstream consumers (notably
 * {@code TriageOrchestratorService}) need to build their execution candidates. Keeps the triage
 * domain from depending on {@code RuleRepository} or {@code RuleEntity} directly.
 */
public record EnabledRuleSnapshot(
        UUID id,
        String displayName,
        int orderIndex,
        String matcherAstJson,
        String actionIntentsJson) {}
