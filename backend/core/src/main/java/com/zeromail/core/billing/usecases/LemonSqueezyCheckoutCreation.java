package com.zeromail.core.billing.usecases;

public record LemonSqueezyCheckoutCreation(
        String checkoutUrl,
        String providerCheckoutId,
        String requestJsonb,
        String responseJsonb,
        String failureReason) {

    public boolean created() {
        return checkoutUrl != null && !checkoutUrl.isBlank();
    }
}
