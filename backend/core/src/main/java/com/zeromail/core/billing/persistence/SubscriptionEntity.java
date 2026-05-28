package com.zeromail.core.billing.persistence;

import com.zeromail.core.shared.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-tenant Lemon Squeezy subscription state. One row per tenant (enforced by {@code
 * uq_subscription_tenant}); a missing row implies the tenant is on the FREE plan.
 *
 * <p>This entity intentionally does NOT extend {@link
 * com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity} so admin/system code paths (LS
 * webhook reconciliation, billing reports) can query across tenants without bypassing the Hibernate
 * {@code @TenantId} filter. Runtime per-tenant access is enforced at the service layer by filtering
 * on {@code tenantId} explicitly.
 *
 * <p>{@code lemonSqueezyProductId} and {@code lemonSqueezyVariantId} are snapshot fields captured
 * at subscription create time so historical plan identity is preserved even if {@link
 * BillingPlanEntity} rows are later edited.
 */
@Entity
@Table(name = "subscription")
public class SubscriptionEntity extends AbstractAuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "lemon_squeezy_subscription_id", nullable = false, updatable = false)
    private long lemonSqueezySubscriptionId;

    @Column(name = "lemon_squeezy_customer_id")
    private Long lemonSqueezyCustomerId;

    @Column(name = "lemon_squeezy_order_id")
    private Long lemonSqueezyOrderId;

    @Column(name = "lemon_squeezy_product_id")
    private Long lemonSqueezyProductId;

    @Column(name = "lemon_squeezy_variant_id")
    private Long lemonSqueezyVariantId;

    @Column(name = "current_period_start")
    private Instant currentPeriodStart;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "last_credit_grant_at")
    private Instant lastCreditGrantAt;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(name = "renews_at")
    private Instant renewsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    protected SubscriptionEntity() {
        // Hibernate
    }

    public SubscriptionEntity(
            UUID id, UUID tenantId, UUID planId, String status, long lemonSqueezySubscriptionId) {
        super(id);
        this.tenantId = tenantId;
        this.planId = planId;
        this.status = status;
        this.lemonSqueezySubscriptionId = lemonSqueezySubscriptionId;
        this.cancelAtPeriodEnd = false;
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

    public long getLemonSqueezySubscriptionId() {
        return lemonSqueezySubscriptionId;
    }

    public Long getLemonSqueezyCustomerId() {
        return lemonSqueezyCustomerId;
    }

    public Long getLemonSqueezyOrderId() {
        return lemonSqueezyOrderId;
    }

    public Long getLemonSqueezyProductId() {
        return lemonSqueezyProductId;
    }

    public Long getLemonSqueezyVariantId() {
        return lemonSqueezyVariantId;
    }

    public Instant getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public Instant getLastCreditGrantAt() {
        return lastCreditGrantAt;
    }

    public boolean isCancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }

    public Instant getRenewsAt() {
        return renewsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public void updatePlan(UUID planId) {
        this.planId = planId;
    }

    public void updateStatus(String status) {
        this.status = status;
    }

    public void updateLemonSqueezyMetadata(
            Long customerId, Long orderId, Long productId, Long variantId) {
        this.lemonSqueezyCustomerId = customerId;
        this.lemonSqueezyOrderId = orderId;
        this.lemonSqueezyProductId = productId;
        this.lemonSqueezyVariantId = variantId;
    }

    public void updateBillingPeriod(Instant currentPeriodStart, Instant currentPeriodEnd) {
        this.currentPeriodStart = currentPeriodStart;
        this.currentPeriodEnd = currentPeriodEnd;
    }

    public void markCreditGranted(Instant grantedAt) {
        this.lastCreditGrantAt = grantedAt;
    }

    public void updateLifecycle(boolean cancelAtPeriodEnd, Instant renewsAt, Instant endsAt) {
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
        this.renewsAt = renewsAt;
        this.endsAt = endsAt;
    }
}
