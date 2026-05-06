package com.zeromail.api.dto.billing;

import com.zeromail.core.billing.model.CreditBalance;

public record BillingBalanceResponse(int availableCredits, int heldCredits, String currency) {

  public static BillingBalanceResponse from(CreditBalance balance) {
    return new BillingBalanceResponse(balance.availableCredits(), balance.heldCredits(), "credits");
  }
}
