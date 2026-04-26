package com.zeromail.core.account;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zeromail.core.account.model.CurrentUserNotFoundException;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.persistence.OnboardingSelectionEntity;
import com.zeromail.core.persistence.OnboardingSelectionRepository;
import com.zeromail.core.persistence.OnboardingStep;

/**
 * Onboarding state-machine application service.
 *
 * <p>Both {@link #selectTemplate(UUID, String)} and {@link #complete(UUID)} require a user
 * to exist for the current tenant. If it does not, we throw {@link CurrentUserNotFoundException}
 * rather than silently no-oping (the previous controller-level behavior could leave a tenant
 * with a saved selection but no advanced user state, while the client saw 2xx).
 */
@Service
public class OnboardingService {

    private final OnboardingSelectionRepository onboarding;
    private final UserRepository users;

    public OnboardingService(OnboardingSelectionRepository onboarding, UserRepository users) {
        this.onboarding = onboarding;
        this.users = users;
    }

    @Transactional
    public void selectTemplate(UUID tenantId, String templateKey) {
        UserEntity user = users.findFirstByTenantId(tenantId)
                .orElseThrow(() -> new CurrentUserNotFoundException(tenantId));
        onboarding.save(new OnboardingSelectionEntity(UUID.randomUUID(), tenantId, templateKey));
        user.advanceTo(OnboardingStep.TEMPLATE_SELECTED);
    }

    @Transactional
    public void complete(UUID tenantId) {
        UserEntity user = users.findFirstByTenantId(tenantId)
                .orElseThrow(() -> new CurrentUserNotFoundException(tenantId));
        user.advanceTo(OnboardingStep.COMPLETE);
    }
}
