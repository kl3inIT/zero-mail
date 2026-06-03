package com.zeromail.core.billing.usecases;

import com.zeromail.core.billing.config.BillingProperties;
import com.zeromail.core.billing.domain.BankTransferCodeGenerator;
import com.zeromail.core.billing.exception.BillingCheckoutUnavailableException;
import com.zeromail.core.billing.exception.BillingPlanDowngradeNotAllowedException;
import com.zeromail.core.billing.exception.BillingPlanNotFoundException;
import com.zeromail.core.billing.persistence.BillingBankTransferIntentEntity;
import com.zeromail.core.billing.persistence.BillingBankTransferIntentRepository;
import com.zeromail.core.billing.persistence.BillingCheckoutSessionEntity;
import com.zeromail.core.billing.persistence.BillingCheckoutSessionRepository;
import com.zeromail.core.billing.persistence.BillingPlanEntity;
import com.zeromail.core.billing.persistence.BillingPlanRepository;
import com.zeromail.core.billing.persistence.FeatureCatalogEntity;
import com.zeromail.core.billing.persistence.PlanFeaturePermissionEntity;
import com.zeromail.core.billing.persistence.PlanFeaturePermissionRepository;
import com.zeromail.core.billing.projection.BankTransferIntentView;
import com.zeromail.core.billing.projection.BillingPlanCatalogView;
import com.zeromail.core.billing.projection.BillingPlanView;
import com.zeromail.core.billing.projection.PlanFeatureSummary;
import com.zeromail.core.billing.projection.PlanUpgradeCheckoutView;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingPlanQueryService {

    private static final Logger log = LoggerFactory.getLogger(BillingPlanQueryService.class);

    private static final String CHECKOUT_STATUS_CREATED = "CREATED";
    private static final String CHECKOUT_STATUS_FAILED = "FAILED";
    private static final String PAYMENT_METHOD_LEMON_SQUEEZY = "LEMON_SQUEEZY";
    private static final String PAYMENT_METHOD_SEPAY_BANK_TRANSFER = "SEPAY_BANK_TRANSFER";
    private static final String BANK_TRANSFER_PROVIDER_SEPAY = "SEPAY";
    private static final String BANK_TRANSFER_STATUS_PENDING = "PENDING";

    private final BillingBankTransferIntentRepository billingBankTransferIntentRepository;
    private final BillingCheckoutSessionRepository billingCheckoutSessionRepository;
    private final BillingPlanRepository billingPlanRepository;
    private final LemonSqueezyCheckoutClient checkoutClient;
    private final Duration checkoutReuseWindow;
    private final BillingProperties.SepayProperties sepay;
    private final PlanFeaturePermissionRepository planFeaturePermissionRepository;
    private final FeatureCatalogCache featureCatalogCache;
    private final CurrentBillingPlanResolver currentBillingPlanResolver;
    private final BankTransferCodeGenerator bankTransferCodeGenerator =
            new BankTransferCodeGenerator();

    public BillingPlanQueryService(
            BillingBankTransferIntentRepository billingBankTransferIntentRepository,
            BillingCheckoutSessionRepository billingCheckoutSessionRepository,
            BillingPlanRepository billingPlanRepository,
            LemonSqueezyCheckoutClient checkoutClient,
            BillingProperties billingProperties,
            PlanFeaturePermissionRepository planFeaturePermissionRepository,
            FeatureCatalogCache featureCatalogCache,
            CurrentBillingPlanResolver currentBillingPlanResolver) {
        this.billingBankTransferIntentRepository = billingBankTransferIntentRepository;
        this.billingCheckoutSessionRepository = billingCheckoutSessionRepository;
        this.billingPlanRepository = billingPlanRepository;
        this.checkoutClient = checkoutClient;
        this.checkoutReuseWindow = billingProperties.lemonSqueezy().checkoutReuseWindow();
        this.sepay = billingProperties.sepay();
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

    @Transactional(noRollbackFor = BillingCheckoutUnavailableException.class)
    public PlanUpgradeCheckoutView createCheckout(
            UUID tenantId, String planCode, String paymentMethod, String userEmail) {
        BillingPlanEntity plan =
                billingPlanRepository
                        .findByCode(planCode)
                        .filter(BillingPlanEntity::isActive)
                        .orElseThrow(BillingPlanNotFoundException::new);
        String normalizedUserEmail = normalizeEmail(userEmail);
        Instant now = Instant.now();
        ensureCheckoutAllowed(tenantId, plan, now);
        String normalizedPaymentMethod = normalizePaymentMethod(paymentMethod);
        if (PAYMENT_METHOD_LEMON_SQUEEZY.equals(normalizedPaymentMethod)) {
            return createLemonSqueezyCheckout(tenantId, plan, normalizedUserEmail, now);
        }
        if (PAYMENT_METHOD_SEPAY_BANK_TRANSFER.equals(normalizedPaymentMethod)) {
            return createSepayBankTransferCheckout(tenantId, plan, normalizedUserEmail, now);
        }
        throw new IllegalArgumentException("Unsupported payment method: " + paymentMethod);
    }

    public Optional<BankTransferIntentView> findBankTransferIntent(UUID tenantId, UUID intentId) {
        return billingBankTransferIntentRepository
                .findByIdAndTenantId(intentId, tenantId)
                .map(BankTransferIntentView::from);
    }

    private PlanUpgradeCheckoutView createLemonSqueezyCheckout(
            UUID tenantId, BillingPlanEntity plan, String normalizedUserEmail, Instant now) {
        Optional<BillingCheckoutSessionEntity> reusableCheckoutSession =
                billingCheckoutSessionRepository.findReusableCheckoutSession(
                        tenantId,
                        plan.getCode(),
                        normalizedUserEmail,
                        CHECKOUT_STATUS_CREATED,
                        now);
        if (reusableCheckoutSession.isPresent()) {
            return PlanUpgradeCheckoutView.lemonSqueezy(
                    reusableCheckoutSession.get().getCheckoutUrl());
        }
        LemonSqueezyCheckoutCreation checkoutCreation =
                checkoutClient.createCheckout(plan, tenantId, normalizedUserEmail);
        billingCheckoutSessionRepository.saveAndFlush(
                checkoutSession(tenantId, normalizedUserEmail, plan, checkoutCreation, now));
        if (!checkoutCreation.created()) {
            log.warn(
                    "event=lemon_squeezy_checkout_failed tenantId={} planCode={} failureReason={}",
                    tenantId,
                    plan.getCode(),
                    checkoutCreation.failureReason());
            throw new BillingCheckoutUnavailableException();
        }
        return PlanUpgradeCheckoutView.lemonSqueezy(checkoutCreation.checkoutUrl());
    }

    private PlanUpgradeCheckoutView createSepayBankTransferCheckout(
            UUID tenantId, BillingPlanEntity plan, String normalizedUserEmail, Instant now) {
        if (!sepay.isConfigured()) {
            throw new BillingCheckoutUnavailableException();
        }
        Optional<BillingBankTransferIntentEntity> reusableIntent =
                billingBankTransferIntentRepository.findReusableBankTransferIntent(
                        tenantId,
                        plan.getId(),
                        BANK_TRANSFER_PROVIDER_SEPAY,
                        BANK_TRANSFER_STATUS_PENDING,
                        now);
        if (reusableIntent.isPresent()) {
            return PlanUpgradeCheckoutView.sepay(BankTransferIntentView.from(reusableIntent.get()));
        }
        String code =
                bankTransferCodeGenerator.generateUniqueCode(
                        candidateCode ->
                                billingBankTransferIntentRepository
                                        .findByCode(candidateCode)
                                        .isEmpty(),
                        3);
        Instant expiresAt = now.plus(sepay.intentReuseWindow());
        String transferContent = buildTransferContent(code, plan.getCode());
        String qrUrl = buildQrUrl(plan.getPriceVnd(), transferContent);
        BillingBankTransferIntentEntity intent =
                new BillingBankTransferIntentEntity(
                        UUID.randomUUID(),
                        tenantId,
                        plan.getId(),
                        plan.getCode(),
                        normalizedUserEmail,
                        BANK_TRANSFER_PROVIDER_SEPAY,
                        code,
                        plan.getPriceVnd(),
                        plan.getCurrency(),
                        BANK_TRANSFER_STATUS_PENDING,
                        expiresAt,
                        sepay.bankCode(),
                        sepay.bankName(),
                        sepay.accountNumber(),
                        sepay.accountName(),
                        transferContent,
                        qrUrl);
        billingBankTransferIntentRepository.saveAndFlush(intent);
        return PlanUpgradeCheckoutView.sepay(BankTransferIntentView.from(intent));
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

    private String normalizePaymentMethod(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return PAYMENT_METHOD_LEMON_SQUEEZY;
        }
        return paymentMethod.trim().toUpperCase();
    }

    private String buildTransferContent(String code, String planCode) {
        return "ZM " + code + " " + planCode;
    }

    private String buildQrUrl(long amountVnd, String transferContent) {
        String separator = sepay.qrBaseUrl().toString().contains("?") ? "&" : "?";
        return sepay.qrBaseUrl()
                + separator
                + "acc="
                + urlEncode(sepay.accountNumber())
                + "&bank="
                + urlEncode(sepay.bankCode())
                + "&amount="
                + amountVnd
                + "&des="
                + urlEncode(transferContent);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
