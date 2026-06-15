package com.zeromail.core.support.usecases;

import com.zeromail.core.support.domain.FeedbackStatus;
import com.zeromail.core.support.domain.FeedbackType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FeedbackListQuery {

    public record Filters(FeedbackStatus status, int limit) {}

    public record FeedbackRow(
            UUID id,
            UUID tenantId,
            FeedbackType type,
            String subject,
            String message,
            String contactEmail,
            FeedbackStatus status,
            String adminNotes,
            Instant resolvedAt,
            Instant createdAt) {}

    public record Result(List<FeedbackRow> rows, long openCount) {}

    private FeedbackListQuery() {}
}
