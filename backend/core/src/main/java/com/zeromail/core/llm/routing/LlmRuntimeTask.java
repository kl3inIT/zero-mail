package com.zeromail.core.llm.routing;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum LlmRuntimeTask implements IdentifiedEnum {
    CHAT_ASSISTANT,
    TRIAGE_ACTION,
    DRAFT_GENERATION,
    RULE_AUTHORING,
    RULE_PREVIEW_SEMANTIC,
    TRIAGE_SEMANTIC,
    DRIFT_CHECK;

    @Override
    public String id() {
        return name();
    }

    public static LlmRuntimeTask fromId(String id) {
        return Stream.of(values())
                .filter(task -> task.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown LlmRuntimeTask id: " + id));
    }
}
