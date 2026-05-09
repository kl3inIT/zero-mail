package com.zeromail.core.llm.model;

import java.util.Objects;

/**
 * Vendor-neutral tool-call result emitted by a model adapter.
 */
public record RawToolCall(String functionName, String argsJson) {

    public RawToolCall {
        Objects.requireNonNull(functionName, "functionName");
        argsJson = argsJson == null ? "{}" : argsJson;
    }
}
