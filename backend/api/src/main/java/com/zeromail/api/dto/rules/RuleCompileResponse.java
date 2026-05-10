package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.application.RuleCompileResult;

public record RuleCompileResponse(
    String status,
    CompiledPayloadResponse compiled,
    ClarificationResponse clarification,
    InvalidCompileResponse invalid) {

  public static RuleCompileResponse from(RuleCompileResult compileResult) {
    return switch (compileResult.status()) {
      case COMPILED ->
          new RuleCompileResponse(
              RuleCompileStatus.COMPILED, CompiledPayloadResponse.from(compileResult), null, null);
      case CLARIFICATION_REQUIRED ->
          new RuleCompileResponse(
              RuleCompileStatus.CLARIFICATION_REQUIRED,
              null,
              ClarificationResponse.from(compileResult),
              null);
      case INVALID ->
          new RuleCompileResponse(
              RuleCompileStatus.INVALID, null, null, InvalidCompileResponse.from(compileResult));
    };
  }
}
