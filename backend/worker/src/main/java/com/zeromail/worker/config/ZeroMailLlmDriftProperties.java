package com.zeromail.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@ConfigurationProperties(prefix = "zero-mail.llm.drift")
@Validated
public record ZeroMailLlmDriftProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("00000000-0000-0000-0000-000000000000") @NotBlank String fixedTenantId,
    @DefaultValue("20") @Min(1) @Max(100) int thresholdPercent) {}
