package com.zeromail.core.billing.usecases;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.billing.exception.FeaturePermissionDeniedException;
import com.zeromail.core.billing.persistence.BillingPlanEntity;
import com.zeromail.core.billing.persistence.BillingPlanRepository;
import com.zeromail.core.billing.persistence.PlanFeaturePermissionEntity;
import com.zeromail.core.billing.persistence.PlanFeaturePermissionRepository;
import com.zeromail.core.billing.persistence.SubscriptionEntity;
import com.zeromail.core.billing.persistence.SubscriptionRepository;
import com.zeromail.core.billing.projection.EffectiveFeaturePermission;
import jakarta.annotation.PostConstruct;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves the effective {@link CallSite} permission for a tenant by joining {@code subscription} →
 * {@code billing_plan} → {@code plan_feature_permission} → {@code feature_catalog}.
 *
 * <h3>Plan fallback</h3>
 *
 * A tenant with no {@code subscription} row is treated as being on the FREE plan. The FREE plan
 * UUID is cached at startup so the fallback path needs no extra DB roundtrip.
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

    private static final Logger log = LoggerFactory.getLogger(FeaturePermissionResolver.class);
    private static final String FREE_PLAN_CODE = "FREE";

    private final SubscriptionRepository subscriptionRepository;
    private final BillingPlanRepository billingPlanRepository;
    private final PlanFeaturePermissionRepository planFeaturePermissionRepository;
    private final FeatureCatalogCache featureCatalogCache;

    private volatile UUID freePlanId;

    public FeaturePermissionResolver(
            SubscriptionRepository subscriptionRepository,
            BillingPlanRepository billingPlanRepository,
            PlanFeaturePermissionRepository planFeaturePermissionRepository,
            FeatureCatalogCache featureCatalogCache) {
        this.subscriptionRepository = subscriptionRepository;
        this.billingPlanRepository = billingPlanRepository;
        this.planFeaturePermissionRepository = planFeaturePermissionRepository;
        this.featureCatalogCache = featureCatalogCache;
    }

    @PostConstruct
    void initialize() {
        BillingPlanEntity freePlan =
                billingPlanRepository
                        .findByCode(FREE_PLAN_CODE)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "billing_plan row missing for FREE — seed via"
                                                        + " Liquibase 099"));
        this.freePlanId = freePlan.getId();
        log.info("event=feature_permission_resolver_initialized free_plan_id={}", freePlanId);
    }

    public EffectiveFeaturePermission resolve(UUID tenantId, CallSite callSite) {
        UUID planId = resolvePlanId(tenantId);
        String planCode = resolvePlanCode(planId);
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

    private UUID resolvePlanId(UUID tenantId) {
        return subscriptionRepository
                .findByTenantId(tenantId)
                .map(SubscriptionEntity::getPlanId)
                .orElse(freePlanId);
    }

    private String resolvePlanCode(UUID planId) {
        if (planId.equals(freePlanId)) {
            return FREE_PLAN_CODE;
        }
        return billingPlanRepository
                .findById(planId)
                .map(BillingPlanEntity::getCode)
                .orElse("UNKNOWN");
    }
}
