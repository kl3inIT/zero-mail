package com.zeromail.core.gmail.domain;

import java.util.NoSuchElementException;
import java.util.stream.Stream;

import com.zeromail.core.shared.lang.IdentifiedEnum;

/**
 * Gmail connection lifecycle states. Implements {@link IdentifiedEnum} only — these states
 * are unordered (PENDING is not "less than" CONNECTED in any sortable sense), so per D-B5
 * we do NOT extend {@link com.zeromail.core.shared.lang.OrderedEnum}.
 *
 * <p><b>D-C2 invariant:</b> {@link #id()} returns {@link Enum#name()} so
 * {@code @Enumerated(EnumType.STRING)} continues to persist the canonical id.
 */
public enum GmailConnectionStatus implements IdentifiedEnum {

    NOT_CONNECTED,
    PENDING,
    CONNECTED,
    DISCONNECTED;

    @Override
    public String id() {
        return name();
    }

    /**
     * Per-impl static fromId per D-B4. Throws {@link NoSuchElementException} on unknown id.
     */
    public static GmailConnectionStatus fromId(String id) {
        return Stream.of(values())
                .filter(e -> e.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown GmailConnectionStatus id: " + id));
    }
}
