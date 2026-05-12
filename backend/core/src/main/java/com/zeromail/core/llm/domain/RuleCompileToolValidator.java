package com.zeromail.core.llm.domain;

import com.zeromail.core.llm.exception.SafetyViolationException;
import com.zeromail.core.llm.usecases.RuleCompileGatewayResult;

public class RuleCompileToolValidator {

    public String validate(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            throw new SafetyViolationException();
        }
        if (!RuleCompileGatewayResult.TOOL_NAME.equals(toolName)) {
            throw new SafetyViolationException();
        }
        return toolName;
    }
}
