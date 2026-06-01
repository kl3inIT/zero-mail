package com.zeromail.api.controllers.credits;

import com.zeromail.api.dto.billing.BillingBalanceResponse;
import com.zeromail.api.dto.billing.BillingLedgerHistoryResponse;
import com.zeromail.core.billing.usecases.BillingAccountQueryService;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "credits")
@RequestMapping("/api/credits")
public class CreditController {

    private final BillingAccountQueryService billingAccountQueryService;

    public CreditController(BillingAccountQueryService billingAccountQueryService) {
        this.billingAccountQueryService = billingAccountQueryService;
    }

    @GetMapping("/balance")
    public BillingBalanceResponse balance() {
        UUID tenantId = TenantContext.currentTenantUuid();
        return BillingBalanceResponse.from(billingAccountQueryService.summary(tenantId));
    }

    @GetMapping("/ledger")
    public BillingLedgerHistoryResponse ledger(
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestParam(name = "cursor", required = false) String cursor) {
        UUID tenantId = TenantContext.currentTenantUuid();
        return BillingLedgerHistoryResponse.from(
                billingAccountQueryService.recentLedger(tenantId, limit, cursor));
    }
}
