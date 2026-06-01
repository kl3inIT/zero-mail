package com.zeromail.core.billing.persistence;

import com.zeromail.core.shared.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One paid plan entitlement created from a successful one-time Lemon Squeezy order. Missing/expired
 * rows mean the tenant falls back to FREE.
 */
@Entity
@Table(name = "billing_plan_period")
public class BillingPlanPeriodEntity extends AbstractAuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "provider", nullable = false, length = 32, updatable = false)
    private String provider;

    @Column(name = "provider_order_id", length = 255, updatable = false)
    private String providerOrderId;

    @Column(name = "provider_checkout_id", length = 255, updatable = false)
    private String providerCheckoutId;

    @Column(name = "provider_event_id", length = 255, updatable = false)
    private String providerEventId;

    @Column(name = "effective_at", nullable = false, updatable = false)
    private Instant effectiveAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "paid_at", nullable = false, updatable = false)
    private Instant paidAt;

    @Column(name = "amount_vnd", nullable = false, updatable = false)
    private long amountVnd;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "lemon_squeezy_customer_id")
    private Long lemonSqueezyCustomerId;

    @Column(name = "lemon_squeezy_product_id")
    private Long lemonSqueezyProductId;

    @Column(name = "lemon_squeezy_variant_id")
    private Long lemonSqueezyVariantId;

    protected BillingPlanPeriodEntity() {
        // Hibernate
    }

    public BillingPlanPeriodEntity(
            UUID id,
            UUID tenantId,
            UUID planId,
            String status,
            String provider,
            String providerOrderId,
            String providerCheckoutId,
            String providerEventId,
            Instant effectiveAt,
            Instant expiresAt,
            Instant paidAt,
            long amountVnd,
            String currency,
            Long lemonSqueezyCustomerId,
            Long lemonSqueezyProductId,
            Long lemonSqueezyVariantId) {
        super(id);
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (planId == null) {
            throw new IllegalArgumentException("planId is required");
        }
        if (effectiveAt == null) {
            throw new IllegalArgumentException("effectiveAt is required");
        }
        if (expiresAt == null || !expiresAt.isAfter(effectiveAt)) {
            throw new IllegalArgumentException("expiresAt must be after effectiveAt");
        }
        if (paidAt == null) {
            throw new IllegalArgumentException("paidAt is required");
        }
        this.tenantId = tenantId;
        this.planId = planId;
        this.status = status;
        this.provider = provider;
        this.providerOrderId = providerOrderId;
        this.providerCheckoutId = providerCheckoutId;
        this.providerEventId = providerEventId;
        this.effectiveAt = effectiveAt;
        this.expiresAt = expiresAt;
        this.paidAt = paidAt;
        this.amountVnd = amountVnd;
        this.currency = currency;
        this.lemonSqueezyCustomerId = lemonSqueezyCustomerId;
        this.lemonSqueezyProductId = lemonSqueezyProductId;
        this.lemonSqueezyVariantId = lemonSqueezyVariantId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public String getStatus() {
        return status;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderOrderId() {
        return providerOrderId;
    }

    public String getProviderCheckoutId() {
        return providerCheckoutId;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public long getAmountVnd() {
        return amountVnd;
    }

    public String getCurrency() {
        return currency;
    }

    public Long getLemonSqueezyCustomerId() {
        return lemonSqueezyCustomerId;
    }

    public Long getLemonSqueezyProductId() {
        return lemonSqueezyProductId;
    }

    public Long getLemonSqueezyVariantId() {
        return lemonSqueezyVariantId;
    }

    public void markExpired() {
        status = "EXPIRED";
    }

    public void markVoided() {
        status = "VOIDED";
    }
}
