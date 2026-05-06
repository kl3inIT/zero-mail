package com.zeromail.core.billing.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@ConfigurationProperties(prefix = "zero-mail.billing")
@Validated
public record BillingProperties(
        @Valid @NotNull SepayProperties sepay,
        @Min(1) @DefaultValue("1000") long vndPerCredit,
        @Min(1) @DefaultValue("5") int maxPendingIntentsPerTenant,
        @DefaultValue("PT24H") Duration intentExpiry) {

    /**
     * Defense-in-depth for accidental Spring placeholder sentinel values. Application yml uses
     * a bare ${SEPAY_WEBHOOK_API_KEY}; this rejects literal default-looking values if one is
     * supplied by mistake.
     */
    public BillingProperties {
        if (sepay != null && sepay.webhookApiKey() != null) {
            String webhookApiKey = sepay.webhookApiKey();
            String lowerCaseWebhookApiKey = webhookApiKey.toLowerCase();
            if (webhookApiKey.startsWith("?")
                    || webhookApiKey.startsWith("$")
                    || lowerCaseWebhookApiKey.contains("must be set")
                    || lowerCaseWebhookApiKey.contains("must be supplied")) {
                throw new IllegalStateException(
                        "zero-mail.billing.sepay.webhook-api-key looks like an unresolved placeholder default. "
                                + "Set SEPAY_WEBHOOK_API_KEY to the real SePay webhook API key from the deployment "
                                + "secret source.");
            }
        }
    }

    public record SepayProperties(@NotBlank String webhookApiKey) {
    }

    @Override
    public String toString() {
        return "BillingProperties[sepay=****, vndPerCredit=" + vndPerCredit
                + ", maxPendingIntentsPerTenant=" + maxPendingIntentsPerTenant
                + ", intentExpiry=" + intentExpiry + "]";
    }
}
