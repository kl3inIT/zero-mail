package com.zeromail.api.dto.billing;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "Webhook acknowledgment response",
        requiredProperties = {"success"})
public record WebhookAcknowledgmentResponse(
        @Schema(
                        description = "Whether the webhook was accepted",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                boolean success) {}
