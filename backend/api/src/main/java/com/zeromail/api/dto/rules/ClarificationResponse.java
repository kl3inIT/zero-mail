package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.usecases.RuleCompileResult;

public record ClarificationResponse(String language, String question) {

    static ClarificationResponse from(RuleCompileResult compileResult) {
        return new ClarificationResponse(
                compileResult.clarificationQuestion().language().id(),
                compileResult.clarificationQuestion().question());
    }
}
