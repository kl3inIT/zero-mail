package com.zeromail.api.dto.admin.scheduler;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.admin.scheduler.usecases.SchedulerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * One background scheduler in the admin schedulers catalog. Read-only in phase 1: {@code
 * triggerable} is always false until a worker control channel exists ("Trigger now" + live
 * last-run/status are scheduler phase 2). {@code nextRunAt} is present only for cron schedulers.
 */
@Schema(
        requiredProperties = {
            "key",
            "displayName",
            "scheduleText",
            "process",
            "category",
            "triggerable"
        })
public record SchedulerResponse(
        String key,
        String displayName,
        String scheduleText,
        @JsonInclude(JsonInclude.Include.NON_NULL) String cronExpression,
        @Schema(allowableValues = {"API", "WORKER"}) String process,
        String category,
        @JsonInclude(JsonInclude.Include.NON_NULL) String description,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant nextRunAt,
        boolean triggerable) {

    public static SchedulerResponse from(SchedulerStatus status) {
        return new SchedulerResponse(
                status.descriptor().key(),
                status.descriptor().displayName(),
                status.descriptor().scheduleText(),
                status.descriptor().cronExpression(),
                status.descriptor().process().name(),
                status.descriptor().category(),
                status.descriptor().description(),
                status.nextRunAt(),
                false);
    }
}
