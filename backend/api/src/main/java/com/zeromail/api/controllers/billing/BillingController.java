package com.zeromail.api.controllers.billing;

import com.zeromail.api.dto.billing.BillingBalanceResponse;
import com.zeromail.api.dto.billing.BillingPackageResponse;
import com.zeromail.api.dto.billing.TopupIntentRequest;
import com.zeromail.api.dto.billing.TopupIntentResponse;
import com.zeromail.core.billing.persistence.BillingTopupIntentEntity;
import com.zeromail.core.billing.usecases.BillingTopupService;
import com.zeromail.core.billing.usecases.CreditLedger;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "billing")
@RequestMapping("/api/billing")
public class BillingController {

    private final CreditLedger creditLedger;
    private final BillingTopupService billingTopupService;

    public BillingController(CreditLedger creditLedger, BillingTopupService billingTopupService) {
        this.creditLedger = creditLedger;
        this.billingTopupService = billingTopupService;
    }

    @GetMapping("/balance")
    public BillingBalanceResponse balance() {
        UUID tenantId = TenantContext.currentTenantUuid();
        return BillingBalanceResponse.from(creditLedger.balance(tenantId));
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
