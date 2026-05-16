package com.zeromail.core.billing.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum PaymentProvider implements IdentifiedEnum {
    SEPAY,
    STRIPE;

    @Override
    public String id() {
        return name();
    }

    public static PaymentProvider fromId(String id) {
        return Stream.of(values())
                .filter(paymentProvider -> paymentProvider.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown PaymentProvider id: " + id));
    }
}
