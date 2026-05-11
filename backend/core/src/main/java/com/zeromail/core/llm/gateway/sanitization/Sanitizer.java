package com.zeromail.core.llm.gateway.sanitization;

import com.zeromail.core.llm.application.SanitizationContext;

@FunctionalInterface
public interface Sanitizer {

    /** Returns a new context with this step's transformation applied. */
    SanitizationContext apply(SanitizationContext context);
}
