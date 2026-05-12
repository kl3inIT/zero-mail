package com.zeromail.core.gmail.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum GmailIngestionHealth implements IdentifiedEnum {
    HEALTHY,
    WATCH_UNHEALTHY,
    HISTORY_LOST;

    @Override
    public String id() {
        return name();
    }

    public static GmailIngestionHealth fromId(String id) {
        return Stream.of(values())
                .filter(e -> e.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () -> new NoSuchElementException("Unknown GmailIngestionHealth id: " + id));
    }
}
