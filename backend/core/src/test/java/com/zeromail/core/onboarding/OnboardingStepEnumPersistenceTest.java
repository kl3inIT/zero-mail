package com.zeromail.core.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.zeromail.core.onboarding.model.OnboardingStep;

/**
 * Wave 0 gap closure for Phase 1.2 — defends against Pitfall 5 / T-01.2-E:
 * Hibernate's {@code @Enumerated(EnumType.STRING)} persists {@code enum.name()},
 * which is package-independent. Moving {@code OnboardingStep} from
 * {@code com.zeromail.core.persistence} to {@code com.zeromail.core.onboarding.model}
 * MUST NOT change the persisted string values. This test makes that contract explicit
 * so a future rename of the enum constants would also fail loudly.
 */
class OnboardingStepEnumPersistenceTest {

    @Test
    void enumConstantsHaveStablePersistedNames() {
        assertThat(OnboardingStep.SIGNED_IN.name()).isEqualTo("SIGNED_IN");
        assertThat(OnboardingStep.GMAIL_CONNECTED.name()).isEqualTo("GMAIL_CONNECTED");
        assertThat(OnboardingStep.TEMPLATE_SELECTED.name()).isEqualTo("TEMPLATE_SELECTED");
        assertThat(OnboardingStep.COMPLETE.name()).isEqualTo("COMPLETE");
    }
}
