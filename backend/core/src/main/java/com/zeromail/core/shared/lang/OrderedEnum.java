package com.zeromail.core.shared.lang;

/**
 * Enum contract for state machines and sortable enums: extends {@link IdentifiedEnum} with an
 * explicit {@link #weight()} method.
 *
 * <p><b>Implements decisions D-B1, D-B5.</b>
 *
 * <p><b>Why weight() not ordinal() (closes REVIEW WR-02):</b> {@link Enum#ordinal()} shifts
 * silently when a constant is inserted in the middle of the enum declaration. A user persisted at
 * {@code TEMPLATE_SELECTED} (ordinal 2) would not be re-mapped, but any code doing {@code
 * next.ordinal() &lt; current.ordinal()} comparisons would compute against the NEW ordinal indices
 * — leading to corruption of forward-only invariants. Explicit {@code weight()} with gaps
 * (10/20/30) lets a future {@code EMAIL_VERIFIED(15)} insert preserve the existing transition
 * relationships.
 *
 * <p><b>Weight gap convention (D-B5):</b> use 10/20/30/40 (NOT 0/1/2/3) so future inserts (e.g.
 * weight 15 between SIGNED_IN(10) and GMAIL_CONNECTED(20)) do not require renumbering persisted
 * higher-weight values. Plan 03 applies this to {@code OnboardingStep}.
 */
public interface OrderedEnum extends IdentifiedEnum {

    /**
     * Sort/transition weight. Use gaps (10/20/30) to allow future inserts without breaking
     * forward-only invariants on already-persisted higher-weight values.
     */
    int weight();
}
