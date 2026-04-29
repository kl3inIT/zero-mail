package com.zeromail.core.gmail.service;

/**
 * Return value from PubSubIngestionService.ingestPushEnvelope.
 * Controller maps these to HTTP responses; business logic stays in core.
 */
public enum IngestResult {
    /** emailAddress not found in gmail_connections; silently drop. */
    UNKNOWN_EMAIL,
    /** messageId already exists in pubsub_delivery; idempotent dedup. */
    DUPLICATE,
    /** Row inserted; worker will process asynchronously. */
    ACCEPTED
}
