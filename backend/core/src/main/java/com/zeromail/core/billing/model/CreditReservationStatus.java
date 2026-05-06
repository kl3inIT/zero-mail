package com.zeromail.core.billing.model;

import java.util.NoSuchElementException;
import java.util.stream.Stream;

import com.zeromail.core.shared.lang.IdentifiedEnum;

/**
 * Lifecycle of a credit reservation sidecar row.
 */
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
                .orElseThrow(() -> new NoSuchElementException("Unknown CreditReservationStatus id: " + id));
    }
}
