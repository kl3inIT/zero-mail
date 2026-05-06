package com.zeromail.api.controllers.billing;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zeromail.api.dto.billing.BillingBalanceResponse;
import com.zeromail.api.dto.billing.TopupIntentRequest;
import com.zeromail.api.dto.billing.TopupIntentResponse;
import com.zeromail.core.billing.model.CreditLedger;
import com.zeromail.core.billing.persistence.BillingTopupIntentEntity;
import com.zeromail.core.billing.service.BillingTopupService;
import com.zeromail.core.tenant.TenantContext;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

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
    UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
    return BillingBalanceResponse.from(creditLedger.balance(tenantId));
  }

  @PostMapping("/topup/intent")
  public TopupIntentResponse createIntent(@Valid @RequestBody TopupIntentRequest request) {
    UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
    BillingTopupIntentEntity intent =
        billingTopupService.createIntent(tenantId, request.amountVnd());
    return toResponse(intent);
  }

  private static TopupIntentResponse toResponse(BillingTopupIntentEntity intent) {
    return new TopupIntentResponse(
        intent.getCode(), intent.getAmountVnd(), intent.getExpiresAt(), null);
  }
}
