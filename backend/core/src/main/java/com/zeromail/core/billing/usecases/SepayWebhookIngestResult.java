package com.zeromail.core.billing.usecases;

import java.util.UUID;

public record SepayWebhookIngestResult(
        UUID eventId, UUID tenantId, String eventName, String redactedPayloadJson) {}
