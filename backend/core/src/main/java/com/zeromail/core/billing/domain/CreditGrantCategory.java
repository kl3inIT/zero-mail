package com.zeromail.core.billing.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum CreditGrantCategory implements IdentifiedEnum {
    BETA,
    MONTHLY_ALLOWANCE,
    PROMOTIONAL,
    PAID,
    ADMIN,
    SERVICE;

    @Override
    public String id() {
        return name();
    }

    public static CreditGrantCategory fromId(String id) {
        return Stream.of(values())
                .filter(creditGrantCategory -> creditGrantCategory.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () -> new NoSuchElementException("Unknown CreditGrantCategory id: " + id));
    }
}
