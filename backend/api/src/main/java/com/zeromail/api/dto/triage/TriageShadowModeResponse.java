package com.zeromail.api.dto.triage;

public record TriageShadowModeResponse(boolean enabled) {

  public static TriageShadowModeResponse from(boolean enabled) {
    return new TriageShadowModeResponse(enabled);
  }
}
