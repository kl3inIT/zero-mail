package com.zeromail.core.billing.persistence;

import com.zeromail.core.shared.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Junction row: per-(plan x feature) permission and optional cost / quota override. Synthetic
 * {@code id} PK keeps the entity inside the {@link AbstractAuditableEntity} hierarchy; the
 * (plan_id, feature_code) pair is enforced unique by the schema constraint {@code
 * uq_plan_feature_permission_plan_feature}.
 *
 * <p>{@code creditCostOverride} {@code null} = use {@link
 * FeatureCatalogEntity#getDefaultCreditCost}. {@code dailyInvocationLimit} / {@code
 * monthlyInvocationLimit} {@code null} = unlimited.
 */
@Entity
@Table(name = "plan_feature_permission")
public class PlanFeaturePermissionEntity extends AbstractAuditableEntity {

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "feature_code", nullable = false, length = 64)
    private String featureCode;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "credit_cost_override")
    private Integer creditCostOverride;

    @Column(name = "daily_invocation_limit")
    private Integer dailyInvocationLimit;

    @Column(name = "monthly_invocation_limit")
    private Integer monthlyInvocationLimit;

    protected PlanFeaturePermissionEntity() {
        // Hibernate
    }

    public PlanFeaturePermissionEntity(UUID id, UUID planId, String featureCode, boolean enabled) {
        super(id);
        this.planId = planId;
        this.featureCode = featureCode;
        this.enabled = enabled;
    }

    public UUID getPlanId() {
        return planId;
    }

    public String getFeatureCode() {
        return featureCode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Integer getCreditCostOverride() {
        return creditCostOverride;
    }

    public Integer getDailyInvocationLimit() {
        return dailyInvocationLimit;
    }

    public Integer getMonthlyInvocationLimit() {
        return monthlyInvocationLimit;
    }

    public void updateEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void updateOverrides(
            Integer creditCostOverride,
            Integer dailyInvocationLimit,
            Integer monthlyInvocationLimit) {
        this.creditCostOverride = creditCostOverride;
        this.dailyInvocationLimit = dailyInvocationLimit;
        this.monthlyInvocationLimit = monthlyInvocationLimit;
    }
}
