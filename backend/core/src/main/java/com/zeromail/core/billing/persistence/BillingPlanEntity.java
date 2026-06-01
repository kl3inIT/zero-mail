package com.zeromail.core.billing.persistence;

import com.zeromail.core.shared.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Catalog row for a billing plan (FREE, PLUS, PRO). Global — not tenant-scoped. A tenant gets paid
 * plan access through active {@link BillingPlanPeriodEntity} rows; no active period means FREE.
 *
 * <p>{@code lemonSqueezyProductId} and {@code lemonSqueezyVariantId} are nullable because the FREE
 * plan has no Lemon Squeezy counterpart and because seeded paid rows are populated by operators
 * after creating the products on the LS dashboard.
 */
@Entity
@Table(name = "billing_plan")
public class BillingPlanEntity extends AbstractAuditableEntity {

    @Column(name = "code", nullable = false, length = 64, updatable = false)
    private String code;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "tier_rank", nullable = false)
    private short tierRank;

    @Column(name = "billing_cycle", nullable = false, length = 16)
    private String billingCycle;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "price_vnd", nullable = false)
    private long priceVnd;

    @Column(name = "monthly_credit_allowance", nullable = false)
    private int monthlyCreditAllowance;

    @Column(name = "lemon_squeezy_product_id")
    private Long lemonSqueezyProductId;

    @Column(name = "lemon_squeezy_variant_id")
    private Long lemonSqueezyVariantId;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected BillingPlanEntity() {
        // Hibernate
    }

    public BillingPlanEntity(
            UUID id,
            String code,
            String displayName,
            short tierRank,
            String billingCycle,
            String currency,
            long priceVnd,
            int monthlyCreditAllowance,
            Long lemonSqueezyProductId,
            Long lemonSqueezyVariantId,
            boolean active,
            int sortOrder) {
        super(id);
        this.code = code;
        this.displayName = displayName;
        this.tierRank = tierRank;
        this.billingCycle = billingCycle;
        this.currency = currency;
        this.priceVnd = priceVnd;
        this.monthlyCreditAllowance = monthlyCreditAllowance;
        this.lemonSqueezyProductId = lemonSqueezyProductId;
        this.lemonSqueezyVariantId = lemonSqueezyVariantId;
        this.active = active;
        this.sortOrder = sortOrder;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public short getTierRank() {
        return tierRank;
    }

    public String getBillingCycle() {
        return billingCycle;
    }

    public String getCurrency() {
        return currency;
    }

    public long getPriceVnd() {
        return priceVnd;
    }

    public int getMonthlyCreditAllowance() {
        return monthlyCreditAllowance;
    }

    public Long getLemonSqueezyProductId() {
        return lemonSqueezyProductId;
    }

    public Long getLemonSqueezyVariantId() {
        return lemonSqueezyVariantId;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void updateLemonSqueezyIds(Long productId, Long variantId) {
        this.lemonSqueezyProductId = productId;
        this.lemonSqueezyVariantId = variantId;
    }

    public void updatePricing(long priceVnd, int monthlyCreditAllowance) {
        this.priceVnd = priceVnd;
        this.monthlyCreditAllowance = monthlyCreditAllowance;
    }

    public void updateDisplay(String displayName, int sortOrder, boolean active) {
        this.displayName = displayName;
        this.sortOrder = sortOrder;
        this.active = active;
    }
}
