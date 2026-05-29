package com.zeromail.core.billing.usecases;

import com.zeromail.core.billing.exception.BillingCheckoutUnavailableException;
import com.zeromail.core.billing.exception.BillingPlanNotFoundException;
import com.zeromail.core.billing.persistence.BillingPlanEntity;
import com.zeromail.core.billing.persistence.BillingPlanRepository;
import com.zeromail.core.billing.persistence.FeatureCatalogEntity;
import com.zeromail.core.billing.persistence.PlanFeaturePermissionEntity;
import com.zeromail.core.billing.persistence.PlanFeaturePermissionRepository;
import com.zeromail.core.billing.persistence.SubscriptionEntity;
import com.zeromail.core.billing.persistence.SubscriptionRepository;
import com.zeromail.core.billing.projection.BillingPlanCatalogView;
import com.zeromail.core.billing.projection.BillingPlanView;
import com.zeromail.core.billing.projection.PlanFeatureSummary;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingPlanQueryService {

    private static final String FREE_PLAN_CODE = "FREE";

    private final BillingPlanRepository billingPlanRepository;
    private final LemonSqueezyCheckoutUrlBuilder checkoutUrlBuilder;
    private final PlanFeaturePermissionRepository planFeaturePermissionRepository;
    private final FeatureCatalogCache featureCatalogCache;
    private final SubscriptionRepository subscriptionRepository;

    public BillingPlanQueryService(
            BillingPlanRepository billingPlanRepository,
            LemonSqueezyCheckoutUrlBuilder checkoutUrlBuilder,
            PlanFeaturePermissionRepository planFeaturePermissionRepository,
            FeatureCatalogCache featureCatalogCache,
            SubscriptionRepository subscriptionRepository) {
        this.billingPlanRepository = billingPlanRepository;
        this.checkoutUrlBuilder = checkoutUrlBuilder;
        this.planFeaturePermissionRepository = planFeaturePermissionRepository;
        this.featureCatalogCache = featureCatalogCache;
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * Catalog + tenant's currently-active plan code in one shot. Drives the pricing-page UI which
     * needs both pieces to flag the current card.
     */
    @Transactional(readOnly = true)
    public BillingPlanCatalogView listCatalog(UUID tenantId) {
        String currentPlanCode = resolveCurrentPlanCode(tenantId);
        List<BillingPlanView> plans =
                billingPlanRepository.findByActiveTrueOrderBySortOrderAscCodeAsc().stream()
                        .map(this::toView)
                        .toList();
        return new BillingPlanCatalogView(currentPlanCode, plans);
    }

    @Transactional(readOnly = true)
    public String createCheckout(UUID tenantId, String planCode, String userEmail) {
        BillingPlanEntity plan =
                billingPlanRepository
                        .findByCode(planCode)
                        .filter(BillingPlanEntity::isActive)
                        .orElseThrow(BillingPlanNotFoundException::new);
        String checkoutUrl = checkoutUrlBuilder.build(plan, tenantId, userEmail);
        if (checkoutUrl == null) {
            throw new BillingCheckoutUnavailableException();
        }
        return checkoutUrl;
    }

    private String resolveCurrentPlanCode(UUID tenantId) {
        return subscriptionRepository
                .findByTenantId(tenantId)
                .map(SubscriptionEntity::getPlanId)
                .flatMap(billingPlanRepository::findById)
                .map(BillingPlanEntity::getCode)
                .orElse(FREE_PLAN_CODE);
    }

    private BillingPlanView toView(BillingPlanEntity plan) {
        List<PlanFeatureSummary> features =
                planFeaturePermissionRepository.findByPlanId(plan.getId()).stream()
                        .filter(PlanFeaturePermissionEntity::isEnabled)
                        .map(this::toFeatureSummary)
                        .flatMap(Optional::stream)
                        .sorted(Comparator.comparingInt(PlanFeatureSummary::sortOrder))
                        .toList();
        return new BillingPlanView(
                plan.getCode(),
                plan.getDisplayName(),
                plan.getTierRank(),
                plan.getBillingCycle(),
                plan.getCurrency(),
                plan.getPriceVnd(),
                plan.getMonthlyCreditAllowance(),
                plan.getSortOrder(),
                features);
    }

    private Optional<PlanFeatureSummary> toFeatureSummary(PlanFeaturePermissionEntity permission) {
        Optional<FeatureCatalogEntity> catalogRow =
                featureCatalogCache.findByCode(permission.getFeatureCode());
        if (catalogRow.isEmpty() || !catalogRow.get().isActive()) {
            return Optional.empty();
        }
        FeatureCatalogEntity row = catalogRow.get();
        int effectiveCost =
                permission.getCreditCostOverride() != null
                        ? permission.getCreditCostOverride()
                        : row.getDefaultCreditCost();
        return Optional.of(
                new PlanFeatureSummary(
                        row.getCode(),
                        row.getDisplayName(),
                        row.getDescription(),
                        row.getCategory(),
                        effectiveCost,
                        permission.getDailyInvocationLimit(),
                        permission.getMonthlyInvocationLimit(),
                        row.getSortOrder()));
    }
}
