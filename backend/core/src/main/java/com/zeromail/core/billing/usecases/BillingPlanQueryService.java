package com.zeromail.core.billing.usecases;

import com.zeromail.core.billing.exception.BillingCheckoutUnavailableException;
import com.zeromail.core.billing.exception.BillingPlanDowngradeNotAllowedException;
import com.zeromail.core.billing.exception.BillingPlanNotFoundException;
import com.zeromail.core.billing.persistence.BillingCheckoutSessionEntity;
import com.zeromail.core.billing.persistence.BillingCheckoutSessionRepository;
import com.zeromail.core.billing.persistence.BillingPlanEntity;
import com.zeromail.core.billing.persistence.BillingPlanRepository;
import com.zeromail.core.billing.persistence.FeatureCatalogEntity;
import com.zeromail.core.billing.persistence.PlanFeaturePermissionEntity;
import com.zeromail.core.billing.persistence.PlanFeaturePermissionRepository;
import com.zeromail.core.billing.projection.BillingPlanCatalogView;
import com.zeromail.core.billing.projection.BillingPlanView;
import com.zeromail.core.billing.projection.PlanFeatureSummary;
import com.zeromail.core.config.ZeroMailCoreProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingPlanQueryService {

    private static final String CHECKOUT_STATUS_CREATED = "CREATED";
    private static final String CHECKOUT_STATUS_FAILED = "FAILED";

    private final BillingCheckoutSessionRepository billingCheckoutSessionRepository;
    private final BillingPlanRepository billingPlanRepository;
    private final LemonSqueezyCheckoutClient checkoutClient;
    private final Duration checkoutReuseWindow;
    private final PlanFeaturePermissionRepository planFeaturePermissionRepository;
    private final FeatureCatalogCache featureCatalogCache;
    private final CurrentBillingPlanResolver currentBillingPlanResolver;

    public BillingPlanQueryService(
            BillingCheckoutSessionRepository billingCheckoutSessionRepository,
            BillingPlanRepository billingPlanRepository,
            LemonSqueezyCheckoutClient checkoutClient,
            ZeroMailCoreProperties coreProperties,
            PlanFeaturePermissionRepository planFeaturePermissionRepository,
            FeatureCatalogCache featureCatalogCache,
            CurrentBillingPlanResolver currentBillingPlanResolver) {
        this.billingCheckoutSessionRepository = billingCheckoutSessionRepository;
        this.billingPlanRepository = billingPlanRepository;
        this.checkoutClient = checkoutClient;
        this.checkoutReuseWindow = coreProperties.billing().lemonSqueezy().checkoutReuseWindow();
        this.planFeaturePermissionRepository = planFeaturePermissionRepository;
        this.featureCatalogCache = featureCatalogCache;
        this.currentBillingPlanResolver = currentBillingPlanResolver;
    }

    /**
     * Catalog + tenant's currently-active plan code in one shot. Drives the pricing-page UI which
     * needs both pieces to flag the current card.
     */
    @Transactional(readOnly = true)
    public BillingPlanCatalogView listCatalog(UUID tenantId) {
        String currentPlanCode = currentBillingPlanResolver.resolveCurrentPlanCode(tenantId);
        List<BillingPlanView> plans =
                billingPlanRepository.findByActiveTrueOrderBySortOrderAscCodeAsc().stream()
                        .map(this::toView)
                        .toList();
        return new BillingPlanCatalogView(currentPlanCode, plans);
    }

    @Transactional
    public String createCheckout(UUID tenantId, String planCode, String userEmail) {
        BillingPlanEntity plan =
                billingPlanRepository
                        .findByCode(planCode)
                        .filter(BillingPlanEntity::isActive)
                        .orElseThrow(BillingPlanNotFoundException::new);
        String normalizedUserEmail = normalizeEmail(userEmail);
        Instant now = Instant.now();
        ensureCheckoutAllowed(tenantId, plan, now);
        Optional<BillingCheckoutSessionEntity> reusableCheckoutSession =
                billingCheckoutSessionRepository.findReusableCheckoutSession(
                        tenantId,
                        plan.getCode(),
                        normalizedUserEmail,
                        CHECKOUT_STATUS_CREATED,
                        now);
        if (reusableCheckoutSession.isPresent()) {
            return reusableCheckoutSession.get().getCheckoutUrl();
        }
        LemonSqueezyCheckoutCreation checkoutCreation =
                checkoutClient.createCheckout(plan, tenantId, normalizedUserEmail);
        billingCheckoutSessionRepository.save(
                checkoutSession(tenantId, normalizedUserEmail, plan, checkoutCreation, now));
        if (!checkoutCreation.created()) {
            throw new BillingCheckoutUnavailableException();
        }
        return checkoutCreation.checkoutUrl();
    }

    private void ensureCheckoutAllowed(UUID tenantId, BillingPlanEntity selectedPlan, Instant now) {
        BillingPlanEntity currentPlan =
                currentBillingPlanResolver.resolveCurrentPlan(tenantId, now);
        if (currentPlan.getTierRank() > selectedPlan.getTierRank()) {
            throw new BillingPlanDowngradeNotAllowedException();
        }
    }

    private BillingCheckoutSessionEntity checkoutSession(
            UUID tenantId,
            String userEmail,
            BillingPlanEntity plan,
            LemonSqueezyCheckoutCreation checkoutCreation,
            Instant createdAt) {
        return new BillingCheckoutSessionEntity(
                UUID.randomUUID(),
                tenantId,
                plan.getCode(),
                userEmail,
                checkoutCreation.providerCheckoutId(),
                checkoutCreation.checkoutUrl(),
                checkoutCreation.created() ? CHECKOUT_STATUS_CREATED : CHECKOUT_STATUS_FAILED,
                checkoutCreation.failureReason(),
                createdAt,
                checkoutCreation.created() ? createdAt.plus(checkoutReuseWindow) : createdAt,
                checkoutCreation.requestJsonb(),
                checkoutCreation.responseJsonb());
    }

    private String normalizeEmail(String userEmail) {
        return userEmail == null || userEmail.isBlank() ? null : userEmail.trim().toLowerCase();
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
