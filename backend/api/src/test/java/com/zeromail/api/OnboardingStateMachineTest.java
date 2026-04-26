package com.zeromail.api;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import com.zeromail.api.controllers.OnboardingController;
import com.zeromail.api.dto.SelectTemplateRequest;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.onboarding.model.OnboardingStep;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
class OnboardingStateMachineTest extends ApiPostgresTestBase {

    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired OnboardingController onboarding;

    @Test
    void forward_transitions_allowed() {
        UUID tenantId = UUID.randomUUID();
        tenants.save(new TenantEntity(tenantId, "t"));

        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(() -> {
            var u = users.save(new UserEntity(UUID.randomUUID(), tenantId, "gs-x", "x@example.com"));
            assertThat(u.getOnboardingStep()).isEqualTo(OnboardingStep.SIGNED_IN);

            onboarding.selectTemplate(new SelectTemplateRequest("archive-receipts"));
            assertThat(users.findById(u.getId()).orElseThrow().getOnboardingStep())
                    .isEqualTo(OnboardingStep.TEMPLATE_SELECTED);

            onboarding.complete();
            assertThat(users.findById(u.getId()).orElseThrow().getOnboardingStep())
                    .isEqualTo(OnboardingStep.COMPLETE);
        });
    }
}
