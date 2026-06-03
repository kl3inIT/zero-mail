package com.zeromail.core.cleanup.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/** User-visible sender state for the Inbox Zero-style bulk unsubscribe table. */
public enum CleanupSenderStatus implements IdentifiedEnum {
    APPROVED,
    UNSUBSCRIBED,
    AUTO_ARCHIVED;

    @Override
    public String id() {
        return name();
    }

    public static CleanupSenderStatus fromId(String id) {
        return Stream.of(values())
                .filter(status -> status.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () -> new NoSuchElementException("Unknown CleanupSenderStatus id: " + id));
    }
}
