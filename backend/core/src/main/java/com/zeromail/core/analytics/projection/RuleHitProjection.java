package com.zeromail.core.analytics.projection;

public record RuleHitProjection(String ruleName, long decisions, long applied, long reverted) {}
