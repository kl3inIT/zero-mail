package com.zeromail.core.admin.spend.projection;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Single row in the top-20 tenants table on /admin/spend. Per R-8F-H3:
 *
 * <ul>
 *   <li>Active tenants surface their {@code gmailAccountEmail} (admin-only data already exposed via
 *       the 8C tenant inspection screen) and a real {@code tenantId} so the admin can click through
 *       to the tenant detail view.
 *   <li>Deleted tenants surface the literal placeholder {@code "[deleted]"} in the email field,
 *       with the row representing an aggregate rollup of one or more deleted tenants.
 *   <li>{@code isKAnonymized = true} when the row collapses fewer than k tenants (default k = 5) —
 *       the {@code tenantId} is then {@code null}, the email is the deleted-rollup placeholder, and
 *       the cost values are aggregated.
 * </ul>
 *
 * <p>{@code unknownPct} is the share of {@code totalCost} attributable to historical rows whose
 * {@code credential_source} is {@code 'UNKNOWN'} per R-8F-H9.
 */
public record TopTenantRow(
        UUID tenantId,
        String gmailAccountEmailOrPlaceholder,
        BigDecimal totalCost,
        BigDecimal unknownCost,
        int callCount,
        boolean isKAnonymized,
        double unknownPct) {

    public TopTenantRow {
        Objects.requireNonNull(
                gmailAccountEmailOrPlaceholder, "gmailAccountEmailOrPlaceholder must not be null");
        Objects.requireNonNull(totalCost, "totalCost must not be null");
        Objects.requireNonNull(unknownCost, "unknownCost must not be null");
    }
}
