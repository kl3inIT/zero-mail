package com.zeromail.core.llm.service;

import org.springframework.stereotype.Component;

import com.zeromail.core.llm.model.RuleCompileGatewayResult;
import com.zeromail.core.llm.model.SafetyViolationException;

@Component
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
