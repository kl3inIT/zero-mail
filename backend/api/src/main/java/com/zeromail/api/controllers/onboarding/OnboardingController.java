package com.zeromail.api.controllers.onboarding;

import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.zeromail.api.dto.onboarding.SelectTemplateRequest;
import com.zeromail.core.onboarding.service.OnboardingService;
import com.zeromail.core.tenant.TenantContext;

import jakarta.validation.Valid;

@RestController
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping("/onboarding/select-template")
    public void selectTemplate(@Valid @RequestBody SelectTemplateRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        onboardingService.selectTemplate(tenantId, request.templateKey());
    }

    @PostMapping("/onboarding/complete")
    public void complete() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        onboardingService.complete(tenantId);
    }
}
