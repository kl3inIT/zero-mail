package com.zeromail.core.llm.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum LlmToolProfile implements IdentifiedEnum {
    SAFE_ACTIONS("safe-actions"),
    RULE_COMPILE("rule-compile"),
    RULE_COMPILE_REVIEW_DRAFT("rule-compile-review-draft"),
    SAVE_DRAFT_ONLY("save-draft-only");

    private final String profileId;

    LlmToolProfile(String profileId) {
        this.profileId = profileId;
    }

    @Override
    public String id() {
        return profileId;
    }

    public static LlmToolProfile fromId(String profileId) {
        return Stream.of(values())
                .filter(toolProfile -> toolProfile.id().equals(profileId))
                .findFirst()
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "Unknown LlmToolProfile id: " + profileId));
    }
}
