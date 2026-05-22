package com.zeromail.worker.billing;

import com.zeromail.core.billing.usecases.CreditGrantResult;
import com.zeromail.core.billing.usecases.CreditGrantService;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BetaCreditGrantJob {

    private static final Logger log = LoggerFactory.getLogger(BetaCreditGrantJob.class);

    private final TenantRepository tenantRepository;
    private final CreditGrantService creditGrantService;

    public BetaCreditGrantJob(
            TenantRepository tenantRepository, CreditGrantService creditGrantService) {
        this.tenantRepository = tenantRepository;
        this.creditGrantService = creditGrantService;
    }

    @Scheduled(cron = "0 15 0 * * *")
    @SchedulerLock(name = "betaCreditGrantJob", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void scheduledGrant() {
        grantCurrentMonthCredits();
    }

    public int grantCurrentMonthCredits() {
        int createdGrantCount = 0;
        for (TenantEntity tenant : tenantRepository.findAll()) {
            boolean created =
                    ScopedValue.where(TenantContext.TENANT, tenant.getId().toString())
                            .call(
                                    () ->
                                            creditGrantService
                                                    .grantCurrentBetaCredits(tenant.getId())
                                                    .map(CreditGrantResult::created)
                                                    .orElse(false));
            if (created) {
                createdGrantCount++;
            }
        }
        if (createdGrantCount > 0) {
            log.info("event=beta_credit_grant_job grantsCreated={}", createdGrantCount);
        }
        return createdGrantCount;
    }
}
