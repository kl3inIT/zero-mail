package com.zeromail.core.chat.domain;

import com.zeromail.core.shared.lang.OrderedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public enum ConfirmationState implements OrderedEnum {
    PENDING(10),
    PROCESSING(20),
    CONFIRMED(30),
    CANCELED(40),
    FAILED(50);

    private final int weight;

    ConfirmationState(int weight) {
        this.weight = weight;
    }

    @Override
    public String id() {
        return name();
    }

    @Override
    public int weight() {
        return weight;
    }

    public static ConfirmationState fromId(String id) {
        return Stream.of(values())
                .filter(confirmationState -> confirmationState.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () -> new NoSuchElementException("Unknown ConfirmationState id: " + id));
    }
}
