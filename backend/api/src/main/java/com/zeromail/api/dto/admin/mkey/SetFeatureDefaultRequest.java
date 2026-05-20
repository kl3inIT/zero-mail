package com.zeromail.api.dto.admin.mkey;

import com.zeromail.api.security.validation.NoSentinelLeak;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.domain.MasterKeyFeature;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(requiredProperties = {"feature", "provider", "reason"})
public record SetFeatureDefaultRequest(
        @NotNull MasterKeyFeature feature,
        @NotNull LlmProvider provider,
        @NotBlank @Size(min = 8, max = 500) @NoSentinelLeak String reason) {}
