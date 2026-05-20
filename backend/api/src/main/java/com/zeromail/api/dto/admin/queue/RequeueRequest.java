package com.zeromail.api.dto.admin.queue;

import com.zeromail.api.security.validation.NoSentinelLeak;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Wire request body for {@code POST /api/admin/queue/dead-letters/{jobId}/requeue}.
 *
 * <p>Intentionally exposes only {@code reason} — no job body field, no overrides for attempts or
 * status. The admin re-queue is mechanical (status, attempts, timers); no field of the underlying
 * row is editable through this DTO.
 */
@Schema(requiredProperties = {"reason"})
public record RequeueRequest(
        @NotBlank(message = "error.admin.reason_too_short") @Size(min = 8, max = 500, message = "error.admin.reason_too_short") @NoSentinelLeak
                String reason) {}
