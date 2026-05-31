package com.zeromail.core.triage.usecases;

import java.time.Instant;
import java.util.UUID;

public record ProtectedSenderListItem(
        UUID id, String pattern, String patternKind, boolean createdByUser, Instant createdAt) {}
