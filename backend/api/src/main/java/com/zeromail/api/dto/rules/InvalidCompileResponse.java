package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.usecases.RuleCompileResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = "reason")
public record InvalidCompileResponse(String reason) {

    static InvalidCompileResponse from(RuleCompileResult compileResult) {
        return new InvalidCompileResponse(compileResult.failureReason());
    }
}
