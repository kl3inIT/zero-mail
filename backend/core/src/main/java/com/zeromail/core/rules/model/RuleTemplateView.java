package com.zeromail.core.rules.model;

public record RuleTemplateView(
    String templateKey,
    int templateVersion,
    String displayName,
    String localizedCopyKey,
    String sourceText,
    String actionSummary,
    RuleTemplateStatus status,
    boolean sourcedFromOnboarding,
    boolean materialized,
    boolean customized) {}
