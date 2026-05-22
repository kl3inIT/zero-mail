package com.zeromail.api.dto.admin.mkey;

import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.domain.MasterKeyFeature;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(requiredProperties = {"feature", "provider"})
public record SetFeatureDefaultRequest(
        @NotNull MasterKeyFeature feature, @NotNull LlmProvider provider) {}
