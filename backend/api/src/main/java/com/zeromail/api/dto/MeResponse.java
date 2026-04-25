package com.zeromail.api.dto;

public record MeResponse(String userId, String tenantId, String email, String onboardingStep, String preferredLanguage) {}
