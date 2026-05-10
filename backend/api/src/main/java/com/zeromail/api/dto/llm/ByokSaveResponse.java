package com.zeromail.api.dto.llm;

import java.time.Instant;

import com.zeromail.core.llm.application.ByokSaveResult;

public record ByokSaveResponse(boolean ok, Instant savedAt) {

  public static ByokSaveResponse from(ByokSaveResult result) {
    return new ByokSaveResponse(result.ok(), result.savedAt());
  }
}
