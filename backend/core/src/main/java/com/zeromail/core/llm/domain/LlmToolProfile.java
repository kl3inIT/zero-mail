package com.zeromail.core.llm.domain;

import java.util.NoSuchElementException;
import java.util.stream.Stream;

import com.zeromail.core.shared.lang.IdentifiedEnum;

public enum LlmToolProfile implements IdentifiedEnum {
  SAFE_ACTIONS("safe-actions"),
  RULE_COMPILE("rule-compile");

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
        .orElseThrow(() -> new NoSuchElementException("Unknown LlmToolProfile id: " + profileId));
  }
}
