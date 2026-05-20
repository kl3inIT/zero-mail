package com.zeromail.core.config;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.llm.domain.BYOKProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "zero-mail")
@Validated
public record ZeroMailCoreProperties(
        @Valid @NotNull CryptoProperties crypto,
        @Valid @DefaultValue GmailProperties gmail,
        @Valid @NotNull BillingProperties billing,
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
            @Valid @NotNull BillingSepayProperties sepay,
            @Valid @NotNull BillingPaymentAccountProperties paymentAccount,
            @Valid @NotNull BillingCostProperties cost,
            @Min(1) @DefaultValue("1000") long vndPerCredit,
            @Min(1) @DefaultValue("5") int maxPendingIntentsPerTenant,
            @DefaultValue("PT24H") Duration intentExpiry) {

        /**
         * Defense-in-depth for accidental Spring placeholder sentinel values. Application yml uses
         * a bare ${SEPAY_WEBHOOK_API_KEY}; this rejects literal default-looking values if one is
         * supplied by mistake.
         */
        public BillingProperties {
            cost = cost == null ? BillingCostProperties.defaults() : cost;
            if (sepay != null && sepay.webhookApiKey() != null) {
                String webhookApiKey = sepay.webhookApiKey();
                String lowerCaseWebhookApiKey = webhookApiKey.toLowerCase();
                if (webhookApiKey.startsWith("?")
                        || webhookApiKey.startsWith("$")
                        || lowerCaseWebhookApiKey.contains("must be set")
                        || lowerCaseWebhookApiKey.contains("must be supplied")) {
                    throw new IllegalStateException(
                            "zero-mail.billing.sepay.webhook-api-key looks like an unresolved placeholder default. "
                                    + "Set SEPAY_WEBHOOK_API_KEY to the real SePay webhook API key from the "
                                    + "deployment secret source.");
                }
            }
        }

        public record BillingSepayProperties(@NotBlank String webhookApiKey) {}

        public record BillingPaymentAccountProperties(
                @NotBlank String bankCode,
                @NotBlank String bankName,
                @NotBlank String accountNumber,
                @NotBlank String accountName,
                @DefaultValue("") String qrPayload) {}

        public record BillingCostProperties(@Min(0) @DefaultValue("0") int triageDeterministic) {

            static BillingCostProperties defaults() {
                return new BillingCostProperties(0);
            }
        }

        @Override
        public @NonNull String toString() {
            return "BillingProperties[sepay=****, vndPerCredit="
                    + vndPerCredit
                    + ", paymentAccount=****"
                    + ", cost="
                    + cost
                    + ", maxPendingIntentsPerTenant="
                    + maxPendingIntentsPerTenant
                    + ", intentExpiry="
                    + intentExpiry
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
            BYOKProvider provider,
            String baseUrl,
            @NotBlank String apiKey,
            String compileModel,
            String driftModel,
            String triageModel,
            Duration connectTimeout,
            Duration readTimeout) {

        static ZeroMailLlmProperties defaults() {
            return new ZeroMailLlmProperties(null, null, null, null, null, null, null, null);
        }

        public ZeroMailLlmProperties {
            provider = provider == null ? BYOKProvider.OPENAI : provider;
            baseUrl = baseUrl == null ? "https://openrouter.ai/api/v1" : baseUrl;
            compileModel = compileModel == null ? "openai/gpt-5.4-nano" : compileModel;
            driftModel = driftModel == null ? "openai/gpt-5.4-nano" : driftModel;
            triageModel = triageModel == null ? "openai/gpt-5.4-nano" : triageModel;
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
        }

        public Map<CallSite, String> modelByCallSite() {
            return Map.of(
                    CallSite.TRIAGE, triageModel,
                    CallSite.DRAFT, triageModel,
                    CallSite.PREVIEW, compileModel,
                    CallSite.TRIAGE_PLATFORM_LLM, triageModel,
                    CallSite.TRIAGE_DETERMINISTIC, triageModel);
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
