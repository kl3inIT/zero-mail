package com.zeromail.api.dto.admin.feedback;

import com.zeromail.core.support.domain.FeedbackStatus;
import com.zeromail.core.support.domain.FeedbackType;
import com.zeromail.core.support.usecases.FeedbackListQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(
        requiredProperties = {
            "id",
            "type",
            "subject",
            "message",
            "contactEmail",
            "status",
            "createdAt"
        })
public record FeedbackRowResponse(
        UUID id,
        UUID tenantId,
        FeedbackType type,
        String subject,
        String message,
        String contactEmail,
        FeedbackStatus status,
        String adminNotes,
        Instant resolvedAt,
        Instant createdAt) {

    public static FeedbackRowResponse from(FeedbackListQuery.FeedbackRow row) {
        return new FeedbackRowResponse(
                row.id(),
                row.tenantId(),
                row.type(),
                row.subject(),
                row.message(),
                row.contactEmail(),
                row.status(),
                row.adminNotes(),
                row.resolvedAt(),
                row.createdAt());
    }
}
