package com.zeromail.core.cleanup.domain;

import com.zeromail.core.shared.lang.OrderedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * Top-level campaign lifecycle state.
 *
 * <p>Transition graph: {@code QUEUED(10) → RUNNING(20) → COMPLETED(30)} or {@code QUEUED(10) →
 * RUNNING(20) → FAILED(40)}. The campaign moves to {@code COMPLETED} once every attempt is terminal
 * (OK or FAILED) — partial failure is still a campaign COMPLETE, with per-attempt {@code FAILED}
 * rows surfacing retry options to the user. {@code FAILED} at the campaign level is reserved for
 * hard, campaign-wide failures (worker dead-letter, Gmail-disconnected mid-run).
 *
 * <p>Weight gaps (10/20/30/40) per CONVENTIONS §4 / D-B5.
 *
 * <p>Values match the DB CHECK constraint on {@code unsubscribe_campaign.status} (changelog 044):
 * {@code 'QUEUED'}, {@code 'RUNNING'}, {@code 'COMPLETED'}, {@code 'FAILED'}.
 *
 * <p><b>D-C2 invariant:</b> {@link #id()} returns {@link Enum#name()}.
 */
public enum CampaignStatus implements OrderedEnum {
    QUEUED(10),
    RUNNING(20),
    COMPLETED(30),
    FAILED(40);

    private final int weight;

    CampaignStatus(int weight) {
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
    public static CampaignStatus fromId(String id) {
        return Stream.of(values())
                .filter(status -> status.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown CampaignStatus id: " + id));
    }
}
