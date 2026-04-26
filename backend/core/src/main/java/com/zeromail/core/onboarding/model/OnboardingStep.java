package com.zeromail.core.onboarding.model;

import java.util.NoSuchElementException;
import java.util.stream.Stream;

import com.zeromail.core.shared.lang.OrderedEnum;

/**
 * Onboarding state-machine values. Implements {@link OrderedEnum} so {@code UserEntity.advanceTo}
 * compares forward-only progression by an explicit {@link #weight()} instead of {@link Enum#ordinal()}
 * (closes REVIEW WR-02; weight gaps allow future inserts like {@code EMAIL_VERIFIED(15)} without
 * breaking persisted higher-weight values).
 *
 * <p><b>D-C2 invariant:</b> {@link #id()} returns {@link Enum#name()} so
 * {@code @Enumerated(EnumType.STRING)} continues to persist the canonical id.
 */
public enum OnboardingStep implements OrderedEnum {

    SIGNED_IN(10),
    GMAIL_CONNECTED(20),
    TEMPLATE_SELECTED(30),
    COMPLETE(40);

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

    /**
     * Per-impl static fromId per D-B4. Throws {@link NoSuchElementException} on unknown id —
     * fail-loud (NOT {@link IllegalArgumentException}).
     */
    public static OnboardingStep fromId(String id) {
        return Stream.of(values())
                .filter(e -> e.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown OnboardingStep id: " + id));
    }
}
