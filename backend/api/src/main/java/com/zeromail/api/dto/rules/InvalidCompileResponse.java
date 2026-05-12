package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.usecases.RuleCompileResult;

public record InvalidCompileResponse(String reason) {

    static InvalidCompileResponse from(RuleCompileResult compileResult) {
        return new InvalidCompileResponse(compileResult.failureReason());
    }
}
