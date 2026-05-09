package com.zeromail.core.rules.model;

import java.time.Instant;

public record RuleStatusView(
    RuleId ruleId,
    String displayName,
    boolean enabled,
    int orderIndex,
    RuleSchemaVersion schemaVersion,
    Integer entityVersion,
    Integer lastPreviewedEntityVersion,
    Instant lastPreviewedAt) {}
