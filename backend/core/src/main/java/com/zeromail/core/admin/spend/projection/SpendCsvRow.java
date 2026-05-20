package com.zeromail.core.admin.spend.projection;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Single row in the spend CSV export. Per T-08-56 carries only metadata — never tenant_id, prompt,
 * completion, or content-shaped fields.
 */
public record SpendCsvRow(
        Instant bucketDate,
        String provider,
        String feature,
        String credentialSource,
        BigDecimal totalCost,
        int callCount) {}
