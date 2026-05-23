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
    SAVE_DRAFT(Action.SAVE_DRAFT),
    MARK_READ(Action.MARK_READ),
    STAR(Action.STAR),
    ADD_TO_DIGEST(Action.ADD_TO_DIGEST),
    MARK_SPAM(Action.MARK_SPAM),
    SEND_REPLY(Action.SEND_REPLY),
    FORWARD_EMAIL(Action.FORWARD_EMAIL),
    SEND_EMAIL(Action.SEND_EMAIL);

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
