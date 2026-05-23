package com.zeromail.core.llm.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum Action implements IdentifiedEnum {
    LABEL("label"),
    ARCHIVE("archive"),
    SAVE_DRAFT("save_draft"),
    MARK_READ("mark_read"),
    STAR("star"),
    ADD_TO_DIGEST("add_to_digest"),
    MARK_SPAM("mark_spam"),
    SEND_REPLY("send_reply"),
    FORWARD_EMAIL("forward_email"),
    SEND_EMAIL("send_email");

    private final String functionName;

    Action(String functionName) {
        this.functionName = functionName;
    }

    @Override
    public String id() {
        return functionName;
    }

    public String functionName() {
        return functionName;
    }

    public static Action fromId(String id) {
        return Stream.of(values())
                .filter(action -> action.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown Action id: " + id));
    }

    public static Action fromFunctionName(String functionName) {
        return Stream.of(values())
                .filter(action -> action.functionName().equals(functionName))
                .findFirst()
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "Unknown Action functionName: " + functionName));
    }
}
