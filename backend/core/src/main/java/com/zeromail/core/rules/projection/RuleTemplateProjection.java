package com.zeromail.core.rules.projection;


import com.zeromail.core.rules.domain.RuleTemplateStatus;
public record RuleTemplateProjection(
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
