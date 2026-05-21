package com.zeromail.api.controllers.billing;

import com.zeromail.api.dto.billing.BillingBalanceResponse;
import com.zeromail.api.dto.billing.BillingLedgerHistoryResponse;
import com.zeromail.api.dto.billing.BillingPackageResponse;
import com.zeromail.api.dto.billing.TopupIntentRequest;
import com.zeromail.api.dto.billing.TopupIntentResponse;
import com.zeromail.core.billing.persistence.BillingTopupIntentEntity;
import com.zeromail.core.billing.usecases.BillingAccountQueryService;
import com.zeromail.core.billing.usecases.BillingTopupService;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "billing")
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingAccountQueryService billingAccountQueryService;
    private final BillingTopupService billingTopupService;

    public BillingController(
            BillingAccountQueryService billingAccountQueryService,
            BillingTopupService billingTopupService) {
        this.billingAccountQueryService = billingAccountQueryService;
        this.billingTopupService = billingTopupService;
    }

    @GetMapping("/balance")
    public BillingBalanceResponse balance() {
        UUID tenantId = TenantContext.currentTenantUuid();
        return BillingBalanceResponse.from(billingAccountQueryService.summary(tenantId));
    }

    @GetMapping("/ledger")
    public BillingLedgerHistoryResponse ledger(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        UUID tenantId = TenantContext.currentTenantUuid();
        return BillingLedgerHistoryResponse.from(
                billingAccountQueryService.recentLedger(tenantId, limit));
    }

    @GetMapping("/packages")
    public List<BillingPackageResponse> packages() {
        return billingTopupService.listActivePackages().stream()
                .map(BillingPackageResponse::from)
                .toList();
    }

    @PostMapping("/topup/intent")
    public TopupIntentResponse createIntent(@Valid @RequestBody TopupIntentRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        BillingTopupIntentEntity intent =
                billingTopupService.createIntent(tenantId, request.packageCode());
        return TopupIntentResponse.from(intent);
    }
}
