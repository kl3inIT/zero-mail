package com.zeromail.core.billing.usecases;

import java.util.UUID;

public record LemonSqueezyWebhookIngestResult(
        UUID eventId, String eventName, String redactedPayloadJson) {}
