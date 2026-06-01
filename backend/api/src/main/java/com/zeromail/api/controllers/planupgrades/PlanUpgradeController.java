package com.zeromail.api.controllers.planupgrades;

import com.zeromail.api.dto.billing.BankTransferIntentResponse;
import com.zeromail.api.dto.billing.BillingCheckoutRequest;
import com.zeromail.api.dto.billing.BillingCheckoutResponse;
import com.zeromail.api.dto.billing.BillingPlanListResponse;
import com.zeromail.core.account.projection.CurrentUserProjection;
import com.zeromail.core.account.usecases.AccountService;
import com.zeromail.core.billing.usecases.BillingPlanQueryService;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Tag(name = "plan-upgrades")
@RequestMapping("/api/plan-upgrades")
public class PlanUpgradeController {

    private final BillingPlanQueryService billingPlanQueryService;
    private final AccountService accountService;

    public PlanUpgradeController(
            BillingPlanQueryService billingPlanQueryService, AccountService accountService) {
        this.billingPlanQueryService = billingPlanQueryService;
        this.accountService = accountService;
    }

    @GetMapping("/plans")
    public BillingPlanListResponse plans() {
        UUID tenantId = TenantContext.currentTenantUuid();
        return BillingPlanListResponse.from(billingPlanQueryService.listCatalog(tenantId));
    }

    @PostMapping("/checkout")
    public BillingCheckoutResponse checkout(@Valid @RequestBody BillingCheckoutRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        CurrentUserProjection currentUser = accountService.requireCurrentUser(tenantId);
        return BillingCheckoutResponse.from(
                billingPlanQueryService.createCheckout(
                        tenantId,
                        request.planCode(),
                        request.paymentMethod(),
                        currentUser.email()));
    }

    @PostMapping("/plans/{planCode}/checkout")
    public BillingCheckoutResponse legacyLemonSqueezyCheckout(@PathVariable String planCode) {
        UUID tenantId = TenantContext.currentTenantUuid();
        CurrentUserProjection currentUser = accountService.requireCurrentUser(tenantId);
        return BillingCheckoutResponse.from(
                billingPlanQueryService.createCheckout(
                        tenantId, planCode, "LEMON_SQUEEZY", currentUser.email()));
    }

    @GetMapping("/bank-transfer-intents/{intentId}")
    public BankTransferIntentResponse bankTransferIntent(@PathVariable UUID intentId) {
        UUID tenantId = TenantContext.currentTenantUuid();
        return billingPlanQueryService
                .findBankTransferIntent(tenantId, intentId)
                .map(BankTransferIntentResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
