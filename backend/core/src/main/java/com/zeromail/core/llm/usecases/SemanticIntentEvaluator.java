package com.zeromail.core.llm.usecases;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.llm.routing.PlatformLlmRouteCredentials;
import java.util.List;

/**
 * Pure-Java seam for semantic-intent structured-output evaluation. Spring AI-specific
 * implementation details stay inside {@code core.llm.gateway.springai}.
 */
public interface SemanticIntentEvaluator {

    SemanticIntentEvaluationResult evaluate(
            CallSite callSite,
            String modelId,
            String sanitizedMessageContent,
            List<SemanticIntentRequest> intents);

    default SemanticIntentEvaluationResult evaluate(
            CallSite callSite,
            String modelId,
            PlatformLlmRouteCredentials routeCredentials,
            String sanitizedMessageContent,
            List<SemanticIntentRequest> intents) {
        return evaluate(callSite, modelId, sanitizedMessageContent, intents);
    }
}
