package com.zeromail.core.cleanup.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * Why a sender / domain landed on a tenant's suppression list.
 *
 * <p>{@code MANUAL} — user added it explicitly. {@code REPLIED} — auto-added because the user
 * replied to this sender at least once (a strong signal that the user is engaged and the sender
 * should never be cleanup-archived again, even if rules later mark them as bulk). {@code AUTO} —
 * system policy (reserved for future heuristics).
 *
 * <p><b>DB invariant:</b> the DB CHECK constraint on {@code sender_suppression.reason} (changelog
 * 043) uses LOWERCASE values ({@code 'manual'}, {@code 'replied'}, {@code 'auto'}). To preserve the
 * CHECK + keep CONVENTIONS §4 enum constants uppercase, {@link #id()} returns the lowercase
 * serialization explicitly and persistence goes through {@link
 * com.zeromail.core.cleanup.persistence.SuppressionReasonAttributeConverter} (auto-applied via
 * {@code @Converter(autoApply = true)}). This breaks the {@code id() == name()} D-C2 invariant
 * intentionally — the converter is the documented D-C3 trigger described in {@link IdentifiedEnum}.
 */
public enum SuppressionReason implements IdentifiedEnum {
    MANUAL("manual"),
    REPLIED("replied"),
    AUTO("auto");

    private final String id;

    SuppressionReason(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    /** Per-impl static fromId per D-B4. Throws {@link NoSuchElementException} on unknown id. */
    public static SuppressionReason fromId(String id) {
        return Stream.of(values())
                .filter(reason -> reason.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () -> new NoSuchElementException("Unknown SuppressionReason id: " + id));
    }
}
