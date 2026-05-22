package com.zeromail.core.admin.cat.projection;

import java.math.BigDecimal;
import java.time.Instant;

public record CatalogModelRow(
        String provider,
        String modelId,
        String displayName,
        boolean defaultModel,
        boolean recommended,
        BigDecimal costPer1kInput,
        BigDecimal costPer1kOutput,
        Instant deprecatedAt,
        long pinnedTenantCount) {}
