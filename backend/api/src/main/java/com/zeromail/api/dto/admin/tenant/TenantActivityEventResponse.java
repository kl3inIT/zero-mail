package com.zeromail.api.dto.admin.tenant;

import com.zeromail.core.admin.tenant.projection.TenantActivityEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(
        requiredProperties = {
            "eventId",
            "occurredAt",
            "eventType",
            "actionLabel",
            "status",
            "source",
            "legacyDataMissing"
        })
public record TenantActivityEventResponse(
        UUID eventId,
        Instant occurredAt,
        String eventType,
        String actionLabel,
        String detail,
        @Schema(allowableValues = {"SUCCESS", "FAILED", "BLOCKED", "PENDING", "UNKNOWN"})
                String status,
        Integer durationSeconds,
        String source,
        boolean legacyDataMissing) {

    public static TenantActivityEventResponse from(TenantActivityEvent tenantActivityEvent) {
        return new TenantActivityEventResponse(
                tenantActivityEvent.eventId(),
                tenantActivityEvent.occurredAt(),
                tenantActivityEvent.eventType(),
                tenantActivityEvent.actionLabel(),
                tenantActivityEvent.detail(),
                tenantActivityEvent.status(),
                tenantActivityEvent.durationSeconds(),
                tenantActivityEvent.source(),
                tenantActivityEvent.legacyDataMissing());
    }
}
