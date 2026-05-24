package com.zeromail.api.dto.admin.mkey;

import com.zeromail.core.admin.mkey.domain.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Provider's failover chain — every remaining credential row, priority-ordered (lowest priority is
 * primary). Key deletion removes rows from this list; append-only admin audit records retain the
 * operator trail.
 */
@Schema(
        name = "ProviderKeyList",
        description = "Priority-ordered failover chain for one provider",
        requiredProperties = {"provider", "keys"})
public record ProviderKeyListResponse(
        @Schema(
                        allowableValues = {
                            "OPENAI",
                            "ANTHROPIC",
                            "GOOGLE",
                            "DEEPSEEK",
                            "OPENROUTER",
                            "ROUTER_9R"
                        })
                LlmProvider provider,
        List<ProviderKeyResponse> keys) {}
