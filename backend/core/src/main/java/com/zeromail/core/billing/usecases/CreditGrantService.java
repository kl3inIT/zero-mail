package com.zeromail.core.billing.usecases;

import com.zeromail.core.billing.domain.CreditGrantCategory;
import com.zeromail.core.billing.domain.CreditGrantStatus;
import com.zeromail.core.billing.persistence.BillingPlanEntity;
import com.zeromail.core.billing.persistence.CreditGrantEntity;
import com.zeromail.core.billing.persistence.CreditGrantRepository;
import com.zeromail.core.billing.persistence.CreditLedgerEntryEntity;
import com.zeromail.core.billing.persistence.CreditLedgerEntryRepository;
import com.zeromail.core.billing.persistence.lowlevel.AdvisoryLockJdbcHelper;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditGrantService {

    public static final ZoneId PLAN_ALLOWANCE_RESET_ZONE =
            CurrentBillingPlanResolver.PLAN_ALLOWANCE_RESET_ZONE;

    private static final Logger log = LoggerFactory.getLogger(CreditGrantService.class);
    private static final String PLAN_PERIOD_REF_TYPE = "PLAN_PERIOD";
    private static final int MONTHLY_ALLOWANCE_GRANT_PRIORITY = 20;

    private final CreditGrantRepository grantRepository;
    private final CreditLedgerEntryRepository entryRepository;
    private final AdvisoryLockJdbcHelper advisoryLockHelper;
    private final CurrentBillingPlanResolver currentBillingPlanResolver;

    public CreditGrantService(
            CreditGrantRepository grantRepository,
            CreditLedgerEntryRepository entryRepository,
            AdvisoryLockJdbcHelper advisoryLockHelper,
            CurrentBillingPlanResolver currentBillingPlanResolver) {
        this.grantRepository = grantRepository;
        this.entryRepository = entryRepository;
        this.advisoryLockHelper = advisoryLockHelper;
        this.currentBillingPlanResolver = currentBillingPlanResolver;
    }

    @Transactional
    public Optional<CreditGrantResult> resetCurrentPlanAllowanceCredits(UUID tenantId) {
        Instant now = Instant.now();
        CurrentBillingPlanResolver.CurrentPlanAllowancePeriod allowancePeriod =
                currentBillingPlanResolver.resolveCurrentPlanAllowancePeriod(tenantId, now);
        BillingPlanEntity billingPlan = allowancePeriod.plan();

        advisoryLockHelper.acquireTenantLock(tenantId);
        expireActiveGrantsPastExpiry(tenantId, now);
        expireActiveAllowanceGrants(
                tenantId, CreditGrantCategory.BETA, "legacy_beta_credit_superseded");
        if (billingPlan.getMonthlyCreditAllowance() <= 0) {
            expireActiveAllowanceGrants(
                    tenantId,
                    CreditGrantCategory.MONTHLY_ALLOWANCE,
                    "plan_allowance_credit_superseded");
            return Optional.empty();
        }

        Optional<CreditGrantEntity> existingGrant =
                grantRepository.findTenantGrantByReference(
                        tenantId,
                        CreditGrantCategory.MONTHLY_ALLOWANCE,
                        PLAN_PERIOD_REF_TYPE,
                        allowancePeriod.referenceId());
        if (existingGrant.isPresent()) {
            return Optional.of(new CreditGrantResult(existingGrant.get().getId(), false));
        }

        expireActiveAllowanceGrants(
                tenantId,
                CreditGrantCategory.MONTHLY_ALLOWANCE,
                "plan_allowance_credit_superseded");
        String ledgerReferenceId = tenantId + ":" + allowancePeriod.referenceId();
        CreditGrantResult creditGrantResult =
                createGrant(
                        tenantId,
                        CreditGrantCategory.MONTHLY_ALLOWANCE,
                        billingPlan.getMonthlyCreditAllowance(),
                        allowancePeriod.effectiveAt(),
                        allowancePeriod.expiresAt(),
                        MONTHLY_ALLOWANCE_GRANT_PRIORITY,
                        PLAN_PERIOD_REF_TYPE,
                        allowancePeriod.referenceId(),
                        ledgerReferenceId);
        log.info(
                "event=plan_allowance_credit_reset tenantId={} planCode={} period={} credits={}",
                tenantId,
                billingPlan.getCode(),
                allowancePeriod.allowanceMonth(),
                billingPlan.getMonthlyCreditAllowance());
        return Optional.of(creditGrantResult);
    }

    public Instant currentPlanResetAt(UUID tenantId) {
        return currentBillingPlanResolver
                .resolveCurrentPlanAllowancePeriod(tenantId, Instant.now())
                .expiresAt();
    }

    private CreditGrantResult createGrant(
            UUID tenantId,
            CreditGrantCategory category,
            int amountCredits,
            Instant effectiveAt,
            Instant expiresAt,
            int priority,
            String grantReferenceType,
            String grantReferenceId,
            String ledgerReferenceId) {
        UUID grantId = UUID.randomUUID();
        CreditGrantEntity grant =
                new CreditGrantEntity(
                        grantId,
                        tenantId,
                        category,
                        CreditGrantStatus.ACTIVE,
                        amountCredits,
                        effectiveAt,
                        expiresAt,
                        priority,
                        grantReferenceType,
                        grantReferenceId);
        grantRepository.saveAndFlush(grant);
        entryRepository.saveAndFlush(
                CreditLedgerEntryEntity.grant(
                        UUID.randomUUID(),
                        tenantId,
                        amountCredits,
                        grantId,
                        grantReferenceType,
                        ledgerReferenceId));
        return new CreditGrantResult(grantId, true);
    }

    private void expireActiveGrantsPastExpiry(UUID tenantId, Instant now) {
        List<CreditGrantEntity> expiredActiveGrants =
                grantRepository.findByTenantIdAndStatusAndExpiresAtBefore(
                        tenantId, CreditGrantStatus.ACTIVE, now);
        for (CreditGrantEntity expiredActiveGrant : expiredActiveGrants) {
            expireGrantAvailableBalance(tenantId, expiredActiveGrant);
            expiredActiveGrant.markExpired();
            grantRepository.save(expiredActiveGrant);
            log.info(
                    "event=credit_grant_expired tenantId={} grantId={}",
                    tenantId,
                    expiredActiveGrant.getId());
        }
    }

    private void expireActiveAllowanceGrants(
            UUID tenantId, CreditGrantCategory category, String eventName) {
        List<CreditGrantEntity> activeAllowanceGrants =
                grantRepository.findTenantGrantsByCategoryAndStatus(
                        tenantId, category, CreditGrantStatus.ACTIVE);
        for (CreditGrantEntity activeAllowanceGrant : activeAllowanceGrants) {
            expireGrantAvailableBalance(tenantId, activeAllowanceGrant);
            activeAllowanceGrant.markExpired();
            grantRepository.save(activeAllowanceGrant);
            log.info(
                    "event={} tenantId={} grantId={}",
                    eventName,
                    tenantId,
                    activeAllowanceGrant.getId());
        }
    }

    private void expireGrantAvailableBalance(UUID tenantId, CreditGrantEntity creditGrant) {
        int availableCredits =
                Math.toIntExact(
                        entryRepository.sumAvailableCreditsForGrant(tenantId, creditGrant.getId()));
        if (availableCredits <= 0) {
            return;
        }
        entryRepository.saveAndFlush(
                CreditLedgerEntryEntity.expire(
                        UUID.randomUUID(),
                        tenantId,
                        availableCredits,
                        creditGrant.getId(),
                        creditGrant.getId().toString()));
    }
}
