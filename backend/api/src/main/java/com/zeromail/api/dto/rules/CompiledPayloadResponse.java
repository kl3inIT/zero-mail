package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.application.RuleCompileResult;

public record CompiledPayloadResponse(
        String status,
        String sourceLanguage,
        String displayName,
        String schemaVersion,
        String matcherAst,
        String actionIntents) {

    static CompiledPayloadResponse from(RuleCompileResult compileResult) {
        return new CompiledPayloadResponse(
                RuleCompileStatus.COMPILED,
                compileResult.sourceLanguage().id(),
                compileResult.displayName(),
                compileResult.schemaVersion().id(),
                compileResult.matcherAst(),
                compileResult.actionIntents());
    }
}
