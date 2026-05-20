package com.zeromail.core.admin.cat.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum Feature implements IdentifiedEnum {
    CHAT,
    TRIAGE,
    DRAFT;

    @JsonValue
    @Override
    public String id() {
        return name();
    }

    @JsonCreator
    public static Feature fromId(String id) {
        return Stream.of(values())
                .filter(feature -> feature.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown Feature id: " + id));
    }
}
