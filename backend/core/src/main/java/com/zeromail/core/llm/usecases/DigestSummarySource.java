package com.zeromail.core.llm.usecases;

/**
 * One message handed to {@link LlmGateway#summarizeDigestItems(java.util.List)} for the weekly
 * content digest.
 *
 * @param ref caller-stable identifier (e.g. the Gmail message id) echoed back on the matching
 *     {@link DigestSummaryLine}; opaque to the gateway
 * @param content the message text to summarize. The gateway sanitizes and truncates it before any
 *     model call and never logs or persists it.
 */
public record DigestSummarySource(String ref, String content) {}
