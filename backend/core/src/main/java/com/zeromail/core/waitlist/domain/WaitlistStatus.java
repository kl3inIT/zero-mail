package com.zeromail.core.waitlist.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * Lifecycle state of a {@code waitlist_email} row.
 *
 * <p>Persisted via {@code @Enumerated(EnumType.STRING)} so the DB column stores {@link #id()},
 * which equals {@link Enum#name()} per the {@link IdentifiedEnum} D-C2 invariant.
 *
 * <p>State machine:
 *
 * <pre>
 *   PENDING ──admin approve──&gt; APPROVED ──worker send ok──&gt; INVITED
 *      │                          │
 *      │                          └──worker send permanent fail──&gt; INVITE_FAILED
 *      │
 *      └──admin reject──&gt; REJECTED
 * </pre>
 *
 * The worker only picks rows with {@code status = APPROVED}; once it transitions to {@code INVITED}
 * or {@code INVITE_FAILED} the cron query stops matching, so retries are naturally bounded.
 */
public enum WaitlistStatus implements IdentifiedEnum {
    PENDING,
    APPROVED,
    REJECTED,
    INVITED,
    INVITE_FAILED;

    @Override
    public String id() {
        return name();
    }

    /**
     * Per-impl static fromId per D-B4. Throws {@link NoSuchElementException} on unknown id —
     * fail-loud (NOT {@link IllegalArgumentException}).
     */
    public static WaitlistStatus fromId(String id) {
        return Stream.of(values())
                .filter(value -> value.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown WaitlistStatus id: " + id));
    }
}
