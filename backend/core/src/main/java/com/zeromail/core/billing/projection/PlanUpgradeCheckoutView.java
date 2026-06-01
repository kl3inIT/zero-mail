package com.zeromail.core.billing.projection;

public record PlanUpgradeCheckoutView(
        String paymentMethod,
        String status,
        String checkoutUrl,
        BankTransferIntentView bankTransferIntent) {

    public static PlanUpgradeCheckoutView lemonSqueezy(String checkoutUrl) {
        return new PlanUpgradeCheckoutView("LEMON_SQUEEZY", "REDIRECT_REQUIRED", checkoutUrl, null);
    }

    public static PlanUpgradeCheckoutView sepay(BankTransferIntentView bankTransferIntent) {
        return new PlanUpgradeCheckoutView(
                "SEPAY_BANK_TRANSFER", "WAITING_FOR_TRANSFER", null, bankTransferIntent);
    }
}
