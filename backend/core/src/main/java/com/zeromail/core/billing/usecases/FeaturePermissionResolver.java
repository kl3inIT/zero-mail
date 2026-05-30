package com.zeromail.core.billing.usecases;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.billing.exception.FeaturePermissionDeniedException;
import com.zeromail.core.billing.persistence.BillingPlanEntity;
import com.zeromail.core.billing.persistence.PlanFeaturePermissionEntity;
import com.zeromail.core.billing.persistence.PlanFeaturePermissionRepository;
import com.zeromail.core.billing.projection.EffectiveFeaturePermission;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Resolves the effective {@link CallSite} permission for a tenant by joining active plan period →
 * {@code billing_plan} → {@code plan_feature_permission} → {@code feature_catalog}.
 *
 * <h3>Plan fallback</h3>
 *
 * A tenant with no active paid plan period is treated as being on the FREE plan.
 *
 * <h3>Cost resolution</h3>
 *
 * Effective cost = {@code plan_feature_permission.credit_cost_override} when present, else {@code
 * feature_catalog.default_credit_cost} (via {@link FeatureCatalogCache}). Plans currently carry no
 * overrides in v1 so this resolves to the catalog default.
 *
 * <h3>Limit enforcement</h3>
 *
 * Daily/monthly invocation limits are returned in the projection but are NOT enforced here — the
 * next phase wires a Redis sliding window. In v1 every plan returns null limits.
 *
 * <h3>Throws</h3>
 *
 * {@link FeaturePermissionDeniedException} when the resolved permission row has {@code enabled =
 * false}, or when no permission row exists for (plan, feature). Maps to HTTP 402.
 */
@Service
public class FeaturePermissionResolver {

    private final CurrentBillingPlanResolver currentBillingPlanResolver;
    private final PlanFeaturePermissionRepository planFeaturePermissionRepository;
    private final FeatureCatalogCache featureCatalogCache;

    public FeaturePermissionResolver(
            CurrentBillingPlanResolver currentBillingPlanResolver,
            PlanFeaturePermissionRepository planFeaturePermissionRepository,
            FeatureCatalogCache featureCatalogCache) {
        this.currentBillingPlanResolver = currentBillingPlanResolver;
        this.planFeaturePermissionRepository = planFeaturePermissionRepository;
        this.featureCatalogCache = featureCatalogCache;
    }

    public EffectiveFeaturePermission resolve(UUID tenantId, CallSite callSite) {
        BillingPlanEntity billingPlan =
                currentBillingPlanResolver.resolveCurrentPlan(tenantId, Instant.now());
        UUID planId = billingPlan.getId();
        String planCode = billingPlan.getCode();
        PlanFeaturePermissionEntity permission =
                planFeaturePermissionRepository
                        .findByPlanIdAndFeatureCode(planId, callSite.id())
                        .orElseThrow(
                                () -> new FeaturePermissionDeniedException(callSite, planCode));

        if (!permission.isEnabled()) {
            throw new FeaturePermissionDeniedException(callSite, planCode);
        }

        int effectiveCost =
                permission.getCreditCostOverride() != null
                        ? permission.getCreditCostOverride()
                        : featureCatalogCache.defaultCost(callSite);

        return new EffectiveFeaturePermission(
                callSite,
                planCode,
                effectiveCost,
                permission.getDailyInvocationLimit(),
                permission.getMonthlyInvocationLimit());
    }
}
