package com.zeromail.core.triage.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * Discriminator for {@link com.zeromail.core.triage.persistence.TriageAuditEntity#source}: tells
 * the undo + analytics paths whether an audit row was produced by the triage rules pipeline ({@link
 * #TRIAGE}, default) or by the bulk-unsubscribe cleanup campaign worker ({@link
 * #CLEANUP_CAMPAIGN}).
 *
 * <p>H-3 Path A: the cleanup module reuses the existing {@code triage_audit} surface rather than
 * introducing a parallel table. The {@code source} column plus the partial index {@code
 * idx_triage_audit_cleanup} (changelog 046) keep undo queries scoped to cleanup-sourced rows so the
 * per-message Gmail unarchive (Plan 07) never touches a row written by triage.
 *
 * <p><b>Module placement (D-17):</b> this enum lives in {@code core.triage.domain}, not {@code
 * core.cleanup}, because it sits on the triage-owned {@code TriageAuditEntity}. Placing it in
 * {@code core.cleanup} would invert the {@code cleanup → triage} Spring Modulith dependency
 * direction.
 *
 * <p>Values match the DB CHECK constraint on {@code triage_audit.source} (changelog 046): {@code
 * 'TRIAGE'}, {@code 'CLEANUP_CAMPAIGN'}.
 */
public enum CleanupAuditSource implements IdentifiedEnum {
    TRIAGE,
    CLEANUP_CAMPAIGN;

    @Override
    public String id() {
        return name();
    }

    /** Per-impl static fromId per D-B4. Throws {@link NoSuchElementException} on unknown id. */
    public static CleanupAuditSource fromId(String id) {
        return Stream.of(values())
                .filter(source -> source.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () -> new NoSuchElementException("Unknown CleanupAuditSource id: " + id));
    }
}
