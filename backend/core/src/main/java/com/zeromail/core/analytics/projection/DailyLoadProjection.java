package com.zeromail.core.analytics.projection;

public record DailyLoadProjection(String day, long observed, long applied, long reverted) {}
