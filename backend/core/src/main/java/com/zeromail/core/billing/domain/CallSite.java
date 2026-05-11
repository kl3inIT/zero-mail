package com.zeromail.core.billing.domain;

import java.util.NoSuchElementException;
import java.util.stream.Stream;

import com.zeromail.core.shared.lang.IdentifiedEnum;

/**
 * Billable call-site cost map. Implements {@link IdentifiedEnum}, not OrderedEnum: there is no
 * progression order, and the integer payload is {@link #cost()} rather than weight.
 *
 * <p><b>Locked membership (D-G3 + Phase 4 D-E1):</b> {@code TRIAGE}, {@code DRAFT}, {@code
 * PREVIEW}, {@code TRIAGE_PLATFORM_LLM}, {@code TRIAGE_DETERMINISTIC}. There is intentionally no
 * BYOK member because BYOK traffic bypasses the ledger entirely. The ledger service contract
 * documents the BYOK skip path.
 *
 * <p><b>Costs:</b> TRIAGE = 1, DRAFT = 2, PREVIEW = 1, TRIAGE_PLATFORM_LLM = 1,
 * TRIAGE_DETERMINISTIC = 0.
 */
public enum CallSite implements IdentifiedEnum {
  TRIAGE(1),
  DRAFT(2),
  PREVIEW(1),
  TRIAGE_PLATFORM_LLM(1),
  TRIAGE_DETERMINISTIC(0);

  private final int cost;

  CallSite(int cost) {
    this.cost = cost;
  }

  public int cost() {
    return cost;
  }

  @Override
  public String id() {
    return name();
  }

  public static CallSite fromId(String id) {
    return Stream.of(values())
        .filter(callSite -> callSite.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("Unknown CallSite id: " + id));
  }
}
