package com.zeromail.core.onboarding.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zeromail.core.account.model.CurrentUserNotFoundException;
import com.zeromail.core.account.service.AccountService;
import com.zeromail.core.onboarding.model.OnboardingStep;
import com.zeromail.core.onboarding.persistence.OnboardingSelectionEntity;
import com.zeromail.core.onboarding.persistence.OnboardingSelectionRepository;

/**
 * Onboarding state-machine application service.
 *
 * <p>Both {@link #selectTemplate(UUID, String)} and {@link #complete(UUID)} require a user
 * to exist for the current tenant. If it does not, {@link CurrentUserNotFoundException}
 * propagates from {@link AccountService#advanceOnboardingStep(UUID, OnboardingStep)} rather
 * than silently no-oping (the previous controller-level behavior could leave a tenant
 * with a saved selection but no advanced user state, while the client saw 2xx).
 *
 * <p>Phase 1.2 reshape (D-D1 — enforced by {@code DomainBoundaryArchTests}): the previous
 * incarnation injected {@code UserRepository} (account domain) directly. Cross-domain reads
 * now go through {@link AccountService}; this service holds only its own
 * {@link OnboardingSelectionRepository}.
 */
@Service
public class OnboardingService {

    private final OnboardingSelectionRepository onboarding;
    private final AccountService accountService;

    public OnboardingService(OnboardingSelectionRepository onboarding, AccountService accountService) {
        this.onboarding = onboarding;
        this.accountService = accountService;
    }

    @Transactional
    public void selectTemplate(UUID tenantId, String templateKey) {
        onboarding.save(new OnboardingSelectionEntity(UUID.randomUUID(), tenantId, templateKey));
        accountService.advanceOnboardingStep(tenantId, OnboardingStep.TEMPLATE_SELECTED);
    }

    @Transactional
    public void complete(UUID tenantId) {
        accountService.advanceOnboardingStep(tenantId, OnboardingStep.COMPLETE);
    }

    /**
     * Deletes all onboarding selections for the given tenant.
     * Single-domain delete only — orchestration of cross-domain cascade lives in
     * {@code AccountDeletionController} per CL-2 + D-D1.
     */
    @Transactional
    public void deleteSelectionsForCurrentTenant(UUID tenantId) {
        onboarding.deleteAll(onboarding.findByTenantId(tenantId));
    }
}
