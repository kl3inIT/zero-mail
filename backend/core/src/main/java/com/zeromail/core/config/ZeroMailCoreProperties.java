package com.zeromail.core.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "zero-mail")
@Validated
public record ZeroMailCoreProperties(
        @Valid @NotNull CryptoProperties crypto,
        @Valid @DefaultValue GmailProperties gmail,
        @Valid @DefaultValue BillingProperties billing,
        @Valid @DefaultValue LlmProperties llm,
        @Valid @DefaultValue AdminProperties admin) {

    public record CryptoProperties(@NotBlank String refreshTokenKeyBase64) {}

    public record AdminProperties(
            List<String> bootstrapEmails,
            @Valid @DefaultValue AdminAuditProperties audit,
            @Valid @DefaultValue AdminSpendProperties spend) {

        public AdminProperties {
            bootstrapEmails = bootstrapEmails == null ? List.of() : List.copyOf(bootstrapEmails);
        }
    }

    public record AdminAuditProperties(@DefaultValue("") String hmacKekBase64) {}

    /**
     * Spend-dashboard tunables per Phase 8F reviews-pass addenda.
     *
     * @param kAnonymityThreshold (R-8F-H6) minimum bucket size before exact per-tenant figures are
     *     exposed; smaller buckets collapse into a rollup row. {@code @Min(1)} is enforced by
     *     {@code @Validated} at bind time — no silent recovery.
     * @param rowLevelClassificationSince (R-8F-H9) boundary date for the credential_source
     *     row-level classification rollout; rows with {@code created_at} before this date are
     *     classified as UNKNOWN. The UI surfaces this date in the 90-day range picker label.
     */
    public record AdminSpendProperties(
            @Min(1) @DefaultValue("5") int kAnonymityThreshold,
            @NotNull @DefaultValue("2026-05-20") LocalDate rowLevelClassificationSince) {}

    public record GmailProperties(
            @DefaultValue("https://gmail.googleapis.com/") @NotBlank String apiRootUrl,
            @DefaultValue("https://oauth2.googleapis.com/token") @NotNull URI oauthTokenUrl) {

        static GmailProperties defaults() {
            return new GmailProperties(
                    "https://gmail.googleapis.com/",
                    URI.create("https://oauth2.googleapis.com/token"));
        }
    }

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
         * Lemon Squeezy integration config. All fields nullable so the app boots in test/dev
         * contexts without LS credentials; {@link #isConfigured()} reports readiness and services
         * that require LS throw a clear error when called against an unconfigured instance.
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
                        apiBaseUrl == null
                                ? URI.create("https://api.lemonsqueezy.com/v1")
                                : apiBaseUrl;
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

    public record LlmProperties(
            @Valid ZeroMailLlmProperties platform, @Valid ZeroMailLlmByokProperties byok) {

        static LlmProperties defaults() {
            return new LlmProperties(null, null);
        }

        public LlmProperties {
            platform = platform == null ? ZeroMailLlmProperties.defaults() : platform;
            byok = byok == null ? ZeroMailLlmByokProperties.defaults() : byok;
        }
    }

    public record ZeroMailLlmProperties(
            String provider,
            String baseUrl,
            @NotBlank String apiKey,
            String compileModel,
            String driftModel,
            String triageModel,
            String draftModel,
            Duration connectTimeout,
            Duration readTimeout) {

        static ZeroMailLlmProperties defaults() {
            return new ZeroMailLlmProperties(null, null, null, null, null, null, null, null, null);
        }

        public ZeroMailLlmProperties {
            provider = provider == null || provider.isBlank() ? "openai" : provider;
            baseUrl = baseUrl == null ? "https://openrouter.ai/api/v1" : baseUrl;
            compileModel = compileModel == null ? "openai/gpt-5.4-nano" : compileModel;
            driftModel = driftModel == null ? "openai/gpt-5.4-nano" : driftModel;
            triageModel = triageModel == null ? "openai/gpt-5.4-nano" : triageModel;
            draftModel = draftModel == null ? "openai/gpt-5.4-nano" : draftModel;
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
        }
    }

    public record ZeroMailLlmByokProperties(
            boolean allowNonVendorEndpoints,
            List<String> allowedExtraHosts,
            Duration connectTimeout,
            Duration readTimeout) {

        static ZeroMailLlmByokProperties defaults() {
            return new ZeroMailLlmByokProperties(false, List.of(), null, null);
        }

        public ZeroMailLlmByokProperties {
            allowedExtraHosts =
                    allowedExtraHosts == null ? List.of() : List.copyOf(allowedExtraHosts);
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(15) : readTimeout;
        }
    }

    @Override
    public @NonNull String toString() {
        return "ZeroMailCoreProperties[crypto=****, gmail="
                + gmail
                + ", billing="
                + billing
                + ", llm=****"
                + ", admin="
                + adminForLog()
                + "]";
    }

    private String adminForLog() {
        if (admin == null) {
            return "null";
        }
        return "AdminProperties[bootstrapEmails="
                + admin.bootstrapEmails().size()
                + " entries, audit.hmacKekBase64=****, spend="
                + admin.spend()
                + "]";
    }
}
