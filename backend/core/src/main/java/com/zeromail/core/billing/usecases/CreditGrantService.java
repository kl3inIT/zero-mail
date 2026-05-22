package com.zeromail.core.billing.usecases;

import com.zeromail.core.billing.domain.CreditGrantCategory;
import com.zeromail.core.billing.domain.CreditGrantStatus;
import com.zeromail.core.billing.persistence.CreditGrantEntity;
import com.zeromail.core.billing.persistence.CreditGrantRepository;
import com.zeromail.core.billing.persistence.CreditLedgerEntryEntity;
import com.zeromail.core.billing.persistence.CreditLedgerEntryRepository;
import com.zeromail.core.billing.persistence.lowlevel.AdvisoryLockJdbcHelper;
import com.zeromail.core.config.ZeroMailCoreProperties;
import com.zeromail.core.tenant.persistence.TenantEntity;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditGrantService {

    public static final ZoneId BETA_CREDIT_ZONE = ZoneId.of(TenantEntity.DEFAULT_TIME_ZONE);

    private static final Logger log = LoggerFactory.getLogger(CreditGrantService.class);
    private static final String BETA_PERIOD_REF_TYPE = "BETA_PERIOD";
    private static final int BETA_GRANT_PRIORITY = 10;

    private final ZeroMailCoreProperties coreProperties;
    private final CreditGrantRepository grantRepository;
    private final CreditLedgerEntryRepository entryRepository;
    private final AdvisoryLockJdbcHelper advisoryLockHelper;

    public CreditGrantService(
            ZeroMailCoreProperties coreProperties,
            CreditGrantRepository grantRepository,
            CreditLedgerEntryRepository entryRepository,
            AdvisoryLockJdbcHelper advisoryLockHelper) {
        this.coreProperties = coreProperties;
        this.grantRepository = grantRepository;
        this.entryRepository = entryRepository;
        this.advisoryLockHelper = advisoryLockHelper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<CreditGrantResult> grantCurrentBetaCredits(UUID tenantId) {
        ZeroMailCoreProperties.BillingProperties.BillingBetaProperties betaProperties =
                coreProperties.billing().beta();
        if (!betaProperties.enabled() || betaProperties.monthlyCredits() <= 0) {
            return Optional.empty();
        }

        advisoryLockHelper.acquireTenantLock(tenantId);
        Instant now = Instant.now();
        expireActiveGrantsPastExpiry(tenantId, now);
        YearMonth period = YearMonth.now(BETA_CREDIT_ZONE);
        return Optional.of(grantBetaCreditsForPeriod(tenantId, betaProperties, period));
    }

    public Instant currentBetaResetAt() {
        YearMonth nextPeriod = YearMonth.now(BETA_CREDIT_ZONE).plusMonths(1);
        return nextPeriod.atDay(1).atStartOfDay(BETA_CREDIT_ZONE).toInstant();
    }

    private CreditGrantResult grantBetaCreditsForPeriod(
            UUID tenantId,
            ZeroMailCoreProperties.BillingProperties.BillingBetaProperties betaProperties,
            YearMonth period) {
        String periodKey = period.toString();
        String ledgerReferenceId = tenantId + ":" + periodKey;
        Optional<CreditGrantEntity> existingGrant =
                grantRepository.findByTenantIdAndCategoryAndRefTypeAndRefId(
                        tenantId, CreditGrantCategory.BETA, BETA_PERIOD_REF_TYPE, periodKey);
        if (existingGrant.isPresent()) {
            return new CreditGrantResult(existingGrant.get().getId(), false);
        }

        UUID grantId = UUID.randomUUID();
        Instant effectiveAt = period.atDay(1).atStartOfDay(BETA_CREDIT_ZONE).toInstant();
        Instant expiresAt =
                period.plusMonths(1).atDay(1).atStartOfDay(BETA_CREDIT_ZONE).toInstant();
        CreditGrantEntity grant =
                new CreditGrantEntity(
                        grantId,
                        tenantId,
                        CreditGrantCategory.BETA,
                        CreditGrantStatus.ACTIVE,
                        betaProperties.monthlyCredits(),
                        effectiveAt,
                        expiresAt,
                        BETA_GRANT_PRIORITY,
                        BETA_PERIOD_REF_TYPE,
                        periodKey);
        grantRepository.saveAndFlush(grant);
        entryRepository.saveAndFlush(
                CreditLedgerEntryEntity.grant(
                        UUID.randomUUID(),
                        tenantId,
                        betaProperties.monthlyCredits(),
                        grantId,
                        BETA_PERIOD_REF_TYPE,
                        ledgerReferenceId));
        log.info(
                "event=beta_credit_granted tenantId={} period={} credits={}",
                tenantId,
                periodKey,
                betaProperties.monthlyCredits());
        return new CreditGrantResult(grantId, true);
    }

    private void expireActiveGrantsPastExpiry(UUID tenantId, Instant now) {
        List<CreditGrantEntity> expiredActiveGrants =
                grantRepository.findByTenantIdAndStatusAndExpiresAtBefore(
                        tenantId, CreditGrantStatus.ACTIVE, now);
        for (CreditGrantEntity expiredActiveGrant : expiredActiveGrants) {
            int availableCredits =
                    Math.toIntExact(
                            entryRepository.sumAvailableCreditsForGrant(
                                    tenantId, expiredActiveGrant.getId()));
            if (availableCredits > 0) {
                entryRepository.saveAndFlush(
                        CreditLedgerEntryEntity.expire(
                                UUID.randomUUID(),
                                tenantId,
                                availableCredits,
                                expiredActiveGrant.getId(),
                                expiredActiveGrant.getId().toString()));
            }
            expiredActiveGrant.markExpired();
            grantRepository.save(expiredActiveGrant);
            log.info(
                    "event=credit_grant_expired tenantId={} grantId={}",
                    tenantId,
                    expiredActiveGrant.getId());
        }
    }
}
