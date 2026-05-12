package com.zeromail.core.billing.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/** Lifecycle of a credit reservation sidecar row. */
public enum CreditReservationStatus implements IdentifiedEnum {
    PENDING,
    SETTLED,
    RELEASED;

    @Override
    public String id() {
        return name();
    }

    public static CreditReservationStatus fromId(String id) {
        return Stream.of(values())
                .filter(status -> status.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "Unknown CreditReservationStatus id: " + id));
    }
}
