package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.usecases.RuleCompileResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"language", "question"})
public record ClarificationResponse(String language, String question) {

    static ClarificationResponse from(RuleCompileResult compileResult) {
        return new ClarificationResponse(
                compileResult.clarificationQuestion().language().id(),
                compileResult.clarificationQuestion().question());
    }
}
