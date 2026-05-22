package com.zeromail.core.billing.usecases;

import com.zeromail.core.billing.domain.CreditBalance;
import com.zeromail.core.billing.persistence.lowlevel.BillingLedgerReadRepository;
import com.zeromail.core.billing.projection.BillingCreditSummary;
import com.zeromail.core.billing.projection.BillingLedgerEntrySnapshot;
import com.zeromail.core.config.ZeroMailCoreProperties;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class BillingAccountQueryService {

    private static final List<String> BETA_CATEGORIES = List.of("BETA");
    private static final List<String> PAID_CATEGORIES = List.of("PAID");

    private final CreditLedger creditLedger;
    private final CreditGrantService creditGrantService;
    private final BillingLedgerReadRepository ledgerReadRepository;
    private final ZeroMailCoreProperties coreProperties;

    public BillingAccountQueryService(
            CreditLedger creditLedger,
            CreditGrantService creditGrantService,
            BillingLedgerReadRepository ledgerReadRepository,
            ZeroMailCoreProperties coreProperties) {
        this.creditLedger = creditLedger;
        this.creditGrantService = creditGrantService;
        this.ledgerReadRepository = ledgerReadRepository;
        this.coreProperties = coreProperties;
    }

    public BillingCreditSummary summary(UUID tenantId) {
        CreditBalance balance = creditLedger.balance(tenantId);
        ZeroMailCoreProperties.BillingProperties.BillingBetaProperties betaProperties =
                coreProperties.billing().beta();
        return new BillingCreditSummary(
                balance.availableCredits(),
                balance.heldCredits(),
                ledgerReadRepository.sumAvailableCreditsForCategories(tenantId, BETA_CATEGORIES),
                ledgerReadRepository.sumAvailableCreditsForCategories(tenantId, PAID_CATEGORIES)
                        + ledgerReadRepository.sumAvailableUnscopedCredits(tenantId),
                betaProperties.monthlyCredits(),
                creditGrantService.currentBetaResetAt(),
                betaProperties.enabled());
    }

    public List<BillingLedgerEntrySnapshot> recentLedger(UUID tenantId, int limit) {
        creditLedger.balance(tenantId);
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        return ledgerReadRepository.findRecentEntries(tenantId, boundedLimit);
    }
}
