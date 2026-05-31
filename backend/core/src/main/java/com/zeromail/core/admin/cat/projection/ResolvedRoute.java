package com.zeromail.core.admin.cat.projection;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.RoutingTier;
import com.zeromail.core.llm.domain.LlmProvider;
import java.util.UUID;

/**
 * One step in the {@link com.zeromail.core.admin.cat.usecases.LlmRouter} failover chain. The router
 * walks tier rows (PRIMARY → FALLBACK → LAST_RESORT) and, within each tier, walks the provider's
 * ACTIVE keys ordered by priority.
 *
 * <p>Routes are returned to callers in walk order. The caller tries each step in sequence and stops
 * on the first one that yields a successful LLM call.
 */
public record ResolvedRoute(
        Feature feature,
        RoutingTier tier,
        LlmProvider provider,
        String modelId,
        UUID keyId,
        int keyPriority) {}
