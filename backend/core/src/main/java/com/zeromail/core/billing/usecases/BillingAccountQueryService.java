package com.zeromail.core.billing.usecases;

import com.zeromail.core.billing.domain.CreditBalance;
import com.zeromail.core.billing.persistence.BillingPlanEntity;
import com.zeromail.core.billing.persistence.lowlevel.BillingLedgerReadRepository;
import com.zeromail.core.billing.projection.BillingCreditSummary;
import com.zeromail.core.billing.projection.BillingLedgerPage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class BillingAccountQueryService {

    private static final List<String> MONTHLY_ALLOWANCE_CATEGORIES = List.of("MONTHLY_ALLOWANCE");
    private static final List<String> ADDITIONAL_CATEGORIES =
            List.of("PAID", "PROMOTIONAL", "ADMIN", "SERVICE");

    private final CreditLedger creditLedger;
    private final CreditGrantService creditGrantService;
    private final CurrentBillingPlanResolver currentBillingPlanResolver;
    private final BillingLedgerReadRepository ledgerReadRepository;

    public BillingAccountQueryService(
            CreditLedger creditLedger,
            CreditGrantService creditGrantService,
            CurrentBillingPlanResolver currentBillingPlanResolver,
            BillingLedgerReadRepository ledgerReadRepository) {
        this.creditLedger = creditLedger;
        this.creditGrantService = creditGrantService;
        this.currentBillingPlanResolver = currentBillingPlanResolver;
        this.ledgerReadRepository = ledgerReadRepository;
    }

    public BillingCreditSummary summary(UUID tenantId) {
        CreditBalance balance = creditLedger.balance(tenantId);
        BillingPlanEntity currentPlan =
                currentBillingPlanResolver.resolveCurrentPlan(tenantId, Instant.now());
        return new BillingCreditSummary(
                balance.availableCredits(),
                balance.heldCredits(),
                ledgerReadRepository.sumAvailableCreditsForCategories(
                        tenantId, MONTHLY_ALLOWANCE_CATEGORIES),
                ledgerReadRepository.sumAvailableCreditsForCategories(
                                tenantId, ADDITIONAL_CATEGORIES)
                        + ledgerReadRepository.sumAvailableUnscopedCredits(tenantId),
                currentPlan.getMonthlyCreditAllowance(),
                creditGrantService.currentPlanResetAt(tenantId));
    }

    public BillingLedgerPage recentLedger(UUID tenantId, int limit, String cursor) {
        creditLedger.balance(tenantId);
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        return ledgerReadRepository.findRecentEntries(tenantId, boundedLimit, cursor);
    }
}
