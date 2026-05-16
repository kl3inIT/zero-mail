package com.zeromail.core.billing.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum PaymentAttemptStatus implements IdentifiedEnum {
    CREATED,
    PENDING,
    SUCCEEDED,
    FAILED,
    EXPIRED;

    @Override
    public String id() {
        return name();
    }

    public static PaymentAttemptStatus fromId(String id) {
        return Stream.of(values())
                .filter(paymentAttemptStatus -> paymentAttemptStatus.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () -> new NoSuchElementException("Unknown PaymentAttemptStatus id: " + id));
    }
}
