package com.zeromail.core.llm.application;


import com.zeromail.core.llm.domain.Action;
import java.util.Map;
import java.util.Objects;

public record ToolCallResult(Action action, Map<String, Object> args) {

  public ToolCallResult {
    Objects.requireNonNull(action, "action");
    args = args == null ? Map.of() : Map.copyOf(args);
  }
}
