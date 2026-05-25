package com.zeromail.core.admin.cat.projection;

import com.zeromail.core.admin.cat.domain.ModelVerificationStatus;
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
        ModelVerificationStatus verificationStatus,
        Instant deprecatedAt,
        long pinnedTenantCount) {}
