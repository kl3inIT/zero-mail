package com.zeromail.core.triage.domain;

import java.util.NoSuchElementException;
import java.util.stream.Stream;

import com.zeromail.core.shared.lang.IdentifiedEnum;

public enum TriageDecision implements IdentifiedEnum {
  PENDING,
  APPLIED,
  SHADOW_LOGGED,
  REJECTED_BY_SAFETY_NET,
  REJECTED_BY_SAFETY_POLICY,
  FAILED,
  REVERTED;

  @Override
  public String id() {
    return name();
  }

  public static TriageDecision fromId(String id) {
    return Stream.of(values())
        .filter(triageDecision -> triageDecision.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("Unknown TriageDecision id: " + id));
  }
}
