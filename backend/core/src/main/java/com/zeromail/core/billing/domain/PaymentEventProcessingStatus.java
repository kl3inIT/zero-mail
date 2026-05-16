package com.zeromail.core.billing.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum PaymentEventProcessingStatus implements IdentifiedEnum {
    RECEIVED,
    PROCESSED,
    IGNORED,
    FAILED;

    @Override
    public String id() {
        return name();
    }

    public static PaymentEventProcessingStatus fromId(String id) {
        return Stream.of(values())
                .filter(
                        paymentEventProcessingStatus ->
                                paymentEventProcessingStatus.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "Unknown PaymentEventProcessingStatus id: " + id));
    }
}
