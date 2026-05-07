package com.zeromail.core.llm.gateway.sanitization;

import com.zeromail.core.llm.model.SanitizationContext;

/**
 * Temporary Plan 01 contract. Plan 02 replaces this interface with the concrete service.
 */
public interface SanitizationPipeline {

    SanitizationContext sanitize(String rawHtml);
}
