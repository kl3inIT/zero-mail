package com.zeromail.core.account.domain;

import com.zeromail.core.shared.lang.OrderedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * Onboarding state-machine values stored on {@code UserEntity}. Implements {@link OrderedEnum} so
 * account state advances by explicit {@link #weight()} instead of {@link Enum#ordinal()}.
 *
 * <p><b>D-C2 invariant:</b> {@link #id()} returns {@link Enum#name()} so
 * {@code @Enumerated(EnumType.STRING)} continues to persist the canonical id.
 */
public enum OnboardingStep implements OrderedEnum {
    GMAIL_CONNECTED(10),
    TEMPLATE_SELECTED(20),
    COMPLETE(30);

    private final int weight;

    OnboardingStep(int weight) {
        this.weight = weight;
    }

    @Override
    public String id() {
        return name();
    }

    @Override
    public int weight() {
        return weight;
    }

    public static OnboardingStep fromId(String id) {
        return Stream.of(values())
                .filter(onboardingStep -> onboardingStep.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown OnboardingStep id: " + id));
    }
}
