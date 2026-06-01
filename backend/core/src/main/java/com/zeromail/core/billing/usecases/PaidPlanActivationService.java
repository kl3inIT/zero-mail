package com.zeromail.core.billing.usecases;

import com.zeromail.core.billing.persistence.BillingPlanEntity;
import com.zeromail.core.billing.persistence.BillingPlanPeriodEntity;
import com.zeromail.core.billing.persistence.BillingPlanPeriodRepository;
import com.zeromail.core.tenant.TenantContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaidPlanActivationService {

    private static final String PLAN_PERIOD_STATUS_ACTIVE = "ACTIVE";

    private final BillingPlanPeriodRepository billingPlanPeriodRepository;
    private final CreditGrantService creditGrantService;
    private final CurrentBillingPlanResolver currentBillingPlanResolver;

    public PaidPlanActivationService(
            BillingPlanPeriodRepository billingPlanPeriodRepository,
            CreditGrantService creditGrantService,
            CurrentBillingPlanResolver currentBillingPlanResolver) {
        this.billingPlanPeriodRepository = billingPlanPeriodRepository;
        this.creditGrantService = creditGrantService;
        this.currentBillingPlanResolver = currentBillingPlanResolver;
    }

    @Transactional(noRollbackFor = PlanActivationException.class)
    public Optional<BillingPlanPeriodEntity> activate(PaidPlanActivationCommand command) {
        Optional<BillingPlanPeriodEntity> existingPlanPeriod =
                billingPlanPeriodRepository.findByProviderAndProviderOrderId(
                        command.provider(), command.providerOrderId());
        if (existingPlanPeriod.isPresent()) {
            return Optional.empty();
        }
        ensurePlanAllowed(command.tenantId(), command.billingPlan(), Instant.now());
        BillingPlanPeriodEntity billingPlanPeriod = createPlanPeriod(command);
        expireOverlappingPlanPeriods(billingPlanPeriod);
        ScopedValue.where(TenantContext.TENANT, command.tenantId().toString())
                .run(() -> creditGrantService.resetCurrentPlanAllowanceCredits(command.tenantId()));
        return Optional.of(billingPlanPeriod);
    }

    private void ensurePlanAllowed(UUID tenantId, BillingPlanEntity selectedPlan, Instant now) {
        BillingPlanEntity currentPlan =
                currentBillingPlanResolver.resolveCurrentPlan(tenantId, now);
        if (currentPlan.getTierRank() > selectedPlan.getTierRank()) {
            throw new PlanActivationException("plan_downgrade_not_allowed");
        }
    }

    private BillingPlanPeriodEntity createPlanPeriod(PaidPlanActivationCommand command) {
        Instant paidAt = command.paidAt().truncatedTo(ChronoUnit.MILLIS);
        Instant expiresAt =
                paidAt.atZone(CreditGrantService.PLAN_ALLOWANCE_RESET_ZONE)
                        .plusMonths(1)
                        .toInstant()
                        .truncatedTo(ChronoUnit.MILLIS);
        BillingPlanPeriodEntity billingPlanPeriod =
                new BillingPlanPeriodEntity(
                        UUID.randomUUID(),
                        command.tenantId(),
                        command.billingPlan().getId(),
                        PLAN_PERIOD_STATUS_ACTIVE,
                        command.provider(),
                        command.providerOrderId(),
                        command.providerCheckoutId(),
                        command.providerEventId(),
                        paidAt,
                        expiresAt,
                        paidAt,
                        command.amountVnd(),
                        command.currency().trim().toUpperCase(Locale.ROOT));
        return billingPlanPeriodRepository.save(billingPlanPeriod);
    }

    private void expireOverlappingPlanPeriods(BillingPlanPeriodEntity currentPlanPeriod) {
        billingPlanPeriodRepository
                .findOverlappingActiveTenantPlanPeriods(
                        currentPlanPeriod.getTenantId(),
                        currentPlanPeriod.getId(),
                        currentPlanPeriod.getEffectiveAt(),
                        currentPlanPeriod.getExpiresAt())
                .forEach(
                        overlappingPlanPeriod -> {
                            overlappingPlanPeriod.markExpired();
                            billingPlanPeriodRepository.save(overlappingPlanPeriod);
                        });
    }

    public record PaidPlanActivationCommand(
            UUID tenantId,
            BillingPlanEntity billingPlan,
            String provider,
            String providerOrderId,
            String providerCheckoutId,
            String providerEventId,
            Instant paidAt,
            long amountVnd,
            String currency) {}

    public static class PlanActivationException extends RuntimeException {

        public PlanActivationException(String message) {
            super(message);
        }
    }
}
