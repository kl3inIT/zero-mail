package com.zeromail.api.dto.rules;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.rules.usecases.RuleCompileResult;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"status"})
public record RuleCompileResponse(
        String status,
        CompiledPayloadResponse compiled,
        ClarificationResponse clarification,
        InvalidCompileResponse invalid) {

    public static RuleCompileResponse from(RuleCompileResult compileResult) {
        return switch (compileResult.status()) {
            case COMPILED ->
                    new RuleCompileResponse(
                            RuleCompileStatus.COMPILED,
                            CompiledPayloadResponse.from(compileResult),
                            null,
                            null);
            case CLARIFICATION_REQUIRED ->
                    new RuleCompileResponse(
                            RuleCompileStatus.CLARIFICATION_REQUIRED,
                            null,
                            ClarificationResponse.from(compileResult),
                            null);
            case INVALID ->
                    new RuleCompileResponse(
                            RuleCompileStatus.INVALID,
                            null,
                            null,
                            InvalidCompileResponse.from(compileResult));
        };
    }
}
