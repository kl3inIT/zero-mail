package com.zeromail.core.notification.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum DigestDeliveryStatus implements IdentifiedEnum {
    PENDING,
    SENT,
    FAILED;

    @Override
    public String id() {
        return name();
    }

    public static DigestDeliveryStatus fromId(String id) {
        return Stream.of(values())
                .filter(digestDeliveryStatus -> digestDeliveryStatus.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () -> new NoSuchElementException("Unknown DigestDeliveryStatus id: " + id));
    }
}
