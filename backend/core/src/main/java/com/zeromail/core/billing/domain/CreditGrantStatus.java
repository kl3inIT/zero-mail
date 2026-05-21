package com.zeromail.core.billing.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum CreditGrantStatus implements IdentifiedEnum {
    ACTIVE,
    DEPLETED,
    EXPIRED,
    VOIDED;

    @Override
    public String id() {
        return name();
    }

    public static CreditGrantStatus fromId(String id) {
        return Stream.of(values())
                .filter(creditGrantStatus -> creditGrantStatus.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () -> new NoSuchElementException("Unknown CreditGrantStatus id: " + id));
    }
}
