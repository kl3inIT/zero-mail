package com.zeromail.core.cleanup.domain;

import com.zeromail.core.shared.lang.OrderedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * Per-sender attempt state machine inside an unsubscribe campaign.
 *
 * <p>Transition graph: {@code PENDING(10) → RUNNING(20) → OK(30)} or {@code PENDING(10) →
 * RUNNING(20) → FAILED(40)}. {@code FAILED} can transition back to {@code PENDING} via {@link
 * com.zeromail.core.cleanup.persistence.UnsubscribeAttemptEntity#resetToPending()} when the user
 * retries an individual sender (Plan 07) or when the worker re-queues a throttle-deferred attempt
 * (Plan 06). {@code OK} is terminal — retry of an OK attempt is a {@link
 * com.zeromail.core.cleanup.exception.CampaignRetryConflictException}.
 *
 * <p>Weight gaps (10/20/30/40) per CONVENTIONS §4 / D-B5 — future state inserts (e.g. a {@code
 * DEFERRED(15)} or {@code AWAITING_USER(35)}) do not require renumbering persisted higher-weight
 * values.
 *
 * <p>Values match the DB CHECK constraint on {@code unsubscribe_attempt.state} (changelog 045):
 * {@code 'PENDING'}, {@code 'RUNNING'}, {@code 'OK'}, {@code 'FAILED'}.
 *
 * <p><b>D-C2 invariant:</b> {@link #id()} returns {@link Enum#name()}.
 */
public enum UnsubscribeAttemptState implements OrderedEnum {
    PENDING(10),
    RUNNING(20),
    OK(30),
    FAILED(40);

    private final int weight;

    UnsubscribeAttemptState(int weight) {
        this.weight = weight;
    }

    @Override
    public String id() {
        return name();
    }

    @Override
    public int weight() {
        return weight;
    }

    /** Per-impl static fromId per D-B4. Throws {@link NoSuchElementException} on unknown id. */
    public static UnsubscribeAttemptState fromId(String id) {
        return Stream.of(values())
                .filter(state -> state.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "Unknown UnsubscribeAttemptState id: " + id));
    }
}
