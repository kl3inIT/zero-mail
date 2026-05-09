package com.zeromail.core.rules.model;

import java.time.Instant;

public record RuleStatusView(
    RuleId ruleId,
    String displayName,
    String sourceText,
    boolean enabled,
    int orderIndex,
    RuleLanguage sourceLanguage,
    RuleSchemaVersion schemaVersion,
    String matcherAst,
    String actionIntents,
    Integer entityVersion,
    Integer lastPreviewedEntityVersion,
    Instant lastPreviewedAt,
    String templateKey,
    Integer templateVersion,
    boolean customized) {}
