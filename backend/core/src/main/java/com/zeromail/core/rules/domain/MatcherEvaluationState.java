package com.zeromail.core.rules.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum MatcherEvaluationState implements IdentifiedEnum {
    MATCHED,
    NOT_MATCHED,
    DEFERRED;

    @JsonValue
    @Override
    public String id() {
        return name();
    }

    @JsonCreator
    public static MatcherEvaluationState fromId(String id) {
        return Stream.of(values())
                .filter(matcherEvaluationState -> matcherEvaluationState.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "Unknown MatcherEvaluationState id: " + id));
    }
}
