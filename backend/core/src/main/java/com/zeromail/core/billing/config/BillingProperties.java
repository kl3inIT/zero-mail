package com.zeromail.core.billing.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Billing config bound to {@code zero-mail.billing.*}.
 *
 * <p>Extracted from the former {@code ZeroMailCoreProperties} god-object (quick task w9t). The
 * bound keys are unchanged; only the Java owner moved. The masked {@code toString()} (LemonSqueezy
 * reported as {@code configured|not_configured}) is a locked decision — never expose the API key or
 * webhook secret via accidental bean logging.
 */
@ConfigurationProperties(prefix = "zero-mail.billing")
@Validated
public record BillingProperties(
        @Valid @NotNull BillingCostProperties cost,
        @Valid @DefaultValue LemonSqueezyProperties lemonSqueezy) {

    public BillingProperties {
        cost = cost == null ? BillingCostProperties.defaults() : cost;
        lemonSqueezy = lemonSqueezy == null ? LemonSqueezyProperties.defaults() : lemonSqueezy;
    }

    public record BillingCostProperties(@Min(0) @DefaultValue("0") int triageDeterministic) {

        static BillingCostProperties defaults() {
            return new BillingCostProperties(0);
        }
    }

    /**
     * Lemon Squeezy integration config. All fields nullable so the app boots in test/dev contexts
     * without LS credentials; {@link #isConfigured()} reports readiness and services that require
     * LS throw a clear error when called against an unconfigured instance.
     */
    public record LemonSqueezyProperties(
            Long storeId,
            String storeSlug,
            String apiKey,
            String webhookSigningSecret,
            @DefaultValue("false") boolean testMode,
            URI apiBaseUrl,
            Duration checkoutReuseWindow) {

        public LemonSqueezyProperties {
            storeSlug = (storeSlug == null || storeSlug.isBlank()) ? null : storeSlug;
            apiKey = (apiKey == null || apiKey.isBlank()) ? null : apiKey;
            webhookSigningSecret =
                    (webhookSigningSecret == null || webhookSigningSecret.isBlank())
                            ? null
                            : webhookSigningSecret;
            apiBaseUrl =
                    apiBaseUrl == null ? URI.create("https://api.lemonsqueezy.com/v1") : apiBaseUrl;
            checkoutReuseWindow =
                    checkoutReuseWindow == null ? Duration.ofMinutes(15) : checkoutReuseWindow;
        }

        static LemonSqueezyProperties defaults() {
            return new LemonSqueezyProperties(
                    null,
                    null,
                    null,
                    null,
                    false,
                    URI.create("https://api.lemonsqueezy.com/v1"),
                    Duration.ofMinutes(15));
        }

        public boolean isConfigured() {
            return storeId != null && apiKey != null;
        }
    }

    @Override
    public @NonNull String toString() {
        return "BillingProperties[cost="
                + cost
                + ", lemonSqueezy="
                + (lemonSqueezy.isConfigured() ? "configured" : "not_configured")
                + "]";
    }
}
