package com.zeromail.core.triage.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class TriageUndoPolicy {

    public static final Duration UNDO_WINDOW = Duration.ofDays(30);

    private TriageUndoPolicy() {}

    public static Instant undoableUntil(Instant appliedAt) {
        return Objects.requireNonNull(appliedAt, "appliedAt must not be null").plus(UNDO_WINDOW);
    }
}
