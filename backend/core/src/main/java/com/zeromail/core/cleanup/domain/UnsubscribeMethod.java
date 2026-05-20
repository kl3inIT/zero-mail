package com.zeromail.core.cleanup.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * Transport used by the worker when attempting an unsubscribe on a sender's behalf.
 *
 * <p>Unordered enum (different transports, no progression semantic) — implements {@link
 * IdentifiedEnum} only, NOT {@link com.zeromail.core.shared.lang.OrderedEnum}.
 *
 * <p>Values match the DB CHECK constraint on {@code unsubscribe_attempt.unsubscribe_method}
 * (changelog 045): {@code 'ONE_CLICK'}, {@code 'MAILTO'}. The third value {@code NONE} is the
 * in-memory marker used by the worker when no List-Unsubscribe header was observed — the row is
 * never persisted with {@code NONE}, only used for routing decisions.
 *
 * <p><b>D-C2 invariant:</b> {@link #id()} returns {@link Enum#name()} so
 * {@code @Enumerated(EnumType.STRING)} continues to persist the canonical id.
 */
public enum UnsubscribeMethod implements IdentifiedEnum {
    ONE_CLICK,
    MAILTO,
    NONE;

    @Override
    public String id() {
        return name();
    }

    /** Per-impl static fromId per D-B4. Throws {@link NoSuchElementException} on unknown id. */
    public static UnsubscribeMethod fromId(String id) {
        return Stream.of(values())
                .filter(method -> method.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () -> new NoSuchElementException("Unknown UnsubscribeMethod id: " + id));
    }
}
