package com.zeromail.core.llm.service;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.llm.application.SemanticIntentRequest;
import java.util.List;
import java.util.Map;

/**
 * Pure-Java seam for semantic-intent structured-output evaluation. Spring AI-specific
 * implementation details stay inside {@code core.llm.gateway.springai}.
 */
public interface SemanticIntentEvaluator {

    Map<String, Boolean> evaluate(
            CallSite callSite, String sanitizedMessageContent, List<SemanticIntentRequest> intents);
}
