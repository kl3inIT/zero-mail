package com.zeromail.core.analytics.projection;

public record ActionMixProjection(String actionType, long applied, long reverted, long failed) {}
