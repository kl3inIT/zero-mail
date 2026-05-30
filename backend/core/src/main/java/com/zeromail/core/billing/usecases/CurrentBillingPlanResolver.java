package com.zeromail.core.billing.usecases;

import com.zeromail.core.billing.persistence.BillingPlanEntity;
import com.zeromail.core.billing.persistence.BillingPlanPeriodEntity;
import com.zeromail.core.billing.persistence.BillingPlanPeriodRepository;
import com.zeromail.core.billing.persistence.BillingPlanRepository;
import com.zeromail.core.tenant.persistence.TenantEntity;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class CurrentBillingPlanResolver {

    static final String FREE_PLAN_CODE = "FREE";
    static final ZoneId PLAN_ALLOWANCE_RESET_ZONE = ZoneId.of(TenantEntity.DEFAULT_TIME_ZONE);

    private final BillingPlanRepository billingPlanRepository;
    private final BillingPlanPeriodRepository billingPlanPeriodRepository;

    CurrentBillingPlanResolver(
            BillingPlanRepository billingPlanRepository,
            BillingPlanPeriodRepository billingPlanPeriodRepository) {
        this.billingPlanRepository = billingPlanRepository;
        this.billingPlanPeriodRepository = billingPlanPeriodRepository;
    }

    String resolveCurrentPlanCode(UUID tenantId) {
        return resolveCurrentPlan(tenantId, Instant.now()).getCode();
    }

    BillingPlanEntity resolveCurrentPlan(UUID tenantId, Instant now) {
        return billingPlanPeriodRepository.findCurrentTenantPlanPeriods(tenantId, now).stream()
                .findFirst()
                .map(BillingPlanPeriodEntity::getPlanId)
                .flatMap(billingPlanRepository::findById)
                .orElseGet(this::freePlan);
    }

    CurrentPlanAllowancePeriod resolveCurrentPlanAllowancePeriod(UUID tenantId, Instant now) {
        java.util.Optional<BillingPlanPeriodEntity> currentPlanPeriod =
                billingPlanPeriodRepository.findCurrentTenantPlanPeriods(tenantId, now).stream()
                        .findFirst();
        if (currentPlanPeriod.isPresent()) {
            BillingPlanPeriodEntity billingPlanPeriod = currentPlanPeriod.get();
            BillingPlanEntity billingPlan =
                    billingPlanRepository
                            .findById(billingPlanPeriod.getPlanId())
                            .orElseGet(this::freePlan);
            return new CurrentPlanAllowancePeriod(
                    billingPlan,
                    billingPlanPeriod.getId().toString(),
                    billingPlanPeriod.getEffectiveAt(),
                    billingPlanPeriod.getExpiresAt(),
                    billingPlan.getCode() + ":" + billingPlanPeriod.getId());
        }

        BillingPlanEntity billingPlan = freePlan();
        YearMonth allowanceMonth = YearMonth.from(now.atZone(PLAN_ALLOWANCE_RESET_ZONE));
        Instant calendarMonthStart =
                allowanceMonth.atDay(1).atStartOfDay(PLAN_ALLOWANCE_RESET_ZONE).toInstant();
        Instant expiresAt =
                allowanceMonth
                        .plusMonths(1)
                        .atDay(1)
                        .atStartOfDay(PLAN_ALLOWANCE_RESET_ZONE)
                        .toInstant();
        Instant effectiveAt = freeAllowanceEffectiveAt(tenantId, calendarMonthStart, now);
        return new CurrentPlanAllowancePeriod(
                billingPlan,
                allowanceMonth.toString(),
                effectiveAt,
                expiresAt,
                billingPlan.getCode() + ":" + allowanceMonth + ":" + effectiveAt.getEpochSecond());
    }

    private Instant freeAllowanceEffectiveAt(
            UUID tenantId, Instant calendarMonthStart, Instant now) {
        return billingPlanPeriodRepository
                .findLatestEndedTenantPlanPeriodAfter(tenantId, calendarMonthStart, now)
                .stream()
                .findFirst()
                .map(BillingPlanPeriodEntity::getExpiresAt)
                .map(endedAt -> monthBoundedInstant(endedAt, calendarMonthStart))
                .orElse(calendarMonthStart);
    }

    private Instant monthBoundedInstant(Instant endedAt, Instant calendarMonthStart) {
        ZonedDateTime endedAtInResetZone = endedAt.atZone(PLAN_ALLOWANCE_RESET_ZONE);
        ZonedDateTime monthStartInResetZone = calendarMonthStart.atZone(PLAN_ALLOWANCE_RESET_ZONE);
        return endedAtInResetZone.isAfter(monthStartInResetZone) ? endedAt : calendarMonthStart;
    }

    private BillingPlanEntity freePlan() {
        return billingPlanRepository
                .findByCode(FREE_PLAN_CODE)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "billing_plan row missing for FREE - seed via Liquibase 099"));
    }

    record CurrentPlanAllowancePeriod(
            BillingPlanEntity plan,
            String allowanceMonth,
            Instant effectiveAt,
            Instant expiresAt,
            String referenceId) {}
}
