package com.zeromail.api.controllers;

import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.zeromail.api.dto.SelectTemplateRequest;
import com.zeromail.core.persistence.OnboardingSelectionEntity;
import com.zeromail.core.persistence.OnboardingSelectionRepository;
import com.zeromail.core.persistence.OnboardingStep;
import com.zeromail.core.persistence.UserRepository;
import com.zeromail.core.tenant.TenantContext;

import jakarta.validation.Valid;

@RestController
public class OnboardingController {

    private final OnboardingSelectionRepository onboarding;
    private final UserRepository users;

    public OnboardingController(OnboardingSelectionRepository onboarding, UserRepository users) {
        this.onboarding = onboarding;
        this.users = users;
    }

    @PostMapping("/onboarding/select-template")
    @Transactional
    public void selectTemplate(@Valid @RequestBody SelectTemplateRequest req) {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        onboarding.save(new OnboardingSelectionEntity(UUID.randomUUID(), tenantId, req.templateKey()));
        users.findFirstByTenantId(tenantId)
                .ifPresent(u -> u.advanceTo(OnboardingStep.TEMPLATE_SELECTED));
    }

    @PostMapping("/onboarding/complete")
    @Transactional
    public void complete() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        users.findFirstByTenantId(tenantId)
                .ifPresent(u -> u.advanceTo(OnboardingStep.COMPLETE));
    }
}
