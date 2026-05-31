package com.zeromail.api.dto.admin.mkey;

import com.zeromail.core.admin.mkey.domain.MasterKeyFeature;
import com.zeromail.core.llm.domain.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(requiredProperties = {"feature", "provider"})
public record SetFeatureDefaultRequest(
        @NotNull MasterKeyFeature feature, @NotNull LlmProvider provider) {}
