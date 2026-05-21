package com.zeromail.api.dto.admin.mkey;

import com.zeromail.core.admin.mkey.domain.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Provider's full failover chain — every credential row, priority-ordered (lowest priority is
 * primary). Includes REVOKED rows so the admin detail page can render the audit-relevant set, not
 * just the routable ones.
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
