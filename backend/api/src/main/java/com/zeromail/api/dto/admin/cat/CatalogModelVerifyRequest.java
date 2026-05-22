package com.zeromail.api.dto.admin.cat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for the manual catalog-model verify endpoint. Model IDs frequently contain '/' (e.g.
 * "openai/gpt-5.4-nano" routed via OpenRouter), which Spring's default {@code @PathVariable}
 * matcher rejects — so we accept the model ID in the body instead, mirroring the disable endpoint.
 */
public record CatalogModelVerifyRequest(
        @NotBlank @Pattern(regexp = "^[a-zA-Z0-9._:/\\-]{1,128}$") String modelId) {}
