package com.zeromail.core.rules.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.zeromail.core.llm.domain.Action;
import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum RuleActionType implements IdentifiedEnum {
    LABEL(Action.LABEL),
    ARCHIVE(Action.ARCHIVE),
    SAVE_DRAFT(Action.SAVE_DRAFT);

    private final Action llmAction;

    RuleActionType(Action llmAction) {
        this.llmAction = llmAction;
    }

    @JsonValue
    @Override
    public String id() {
        return llmAction.id();
    }

    public Action llmAction() {
        return llmAction;
    }

    @JsonCreator
    public static RuleActionType fromId(String id) {
        return Stream.of(values())
                .filter(ruleActionType -> ruleActionType.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown RuleActionType id: " + id));
    }

    public static RuleActionType fromAction(Action action) {
        return Stream.of(values())
                .filter(ruleActionType -> ruleActionType.llmAction == action)
                .findFirst()
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "Unknown RuleActionType action: " + action));
    }
}
