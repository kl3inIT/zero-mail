package com.zeromail.api.controllers.onboarding;

import com.zeromail.api.dto.onboarding.SelectTemplateRequest;
import com.zeromail.core.onboarding.usecases.OnboardingService;
import com.zeromail.core.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping("/api/onboarding/select-template")
    public void selectTemplate(@Valid @RequestBody SelectTemplateRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        onboardingService.selectTemplate(tenantId, request.templateKey());
    }

    @PostMapping("/api/onboarding/complete")
    public void complete() {
        UUID tenantId = TenantContext.currentTenantUuid();
        onboardingService.complete(tenantId);
    }
}
