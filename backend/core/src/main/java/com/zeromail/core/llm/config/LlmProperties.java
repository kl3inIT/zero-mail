package com.zeromail.core.llm.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * LLM config bound to {@code zero-mail.llm.*}.
 *
 * <p>Final destination of the former {@code ZeroMailCoreProperties} LLM subtree (quick task w9t,
 * Task 3b). The relocated nested records {@link PlatformProperties} (platform routing + model pins)
 * and {@link ByokProperties} (bring-your-own-key endpoint allow-list) keep their exact field shapes
 * and compact-constructor defaults; only the owning package moved. Bound keys are unchanged ({@code
 * zero-mail.llm.platform.*}, {@code zero-mail.llm.byok.*}).
 *
 * <p>The masked {@code toString()} keeps the platform api-key out of accidental bean logs — Spring
 * Boot does not auto-mask secrets in {@code @ConfigurationProperties}.
 */
@ConfigurationProperties(prefix = "zero-mail.llm")
@Validated
public record LlmProperties(
        @Valid @DefaultValue PlatformProperties platform,
        @Valid @DefaultValue ByokProperties byok) {

    public LlmProperties {
        platform = platform == null ? PlatformProperties.defaults() : platform;
        byok = byok == null ? ByokProperties.defaults() : byok;
    }

    public record PlatformProperties(
            String provider,
            String baseUrl,
            @NotBlank String apiKey,
            String compileModel,
            String driftModel,
            String triageModel,
            String draftModel,
            Duration connectTimeout,
            Duration readTimeout) {

        public static PlatformProperties defaults() {
            return new PlatformProperties(null, null, null, null, null, null, null, null, null);
        }

        public PlatformProperties {
            provider = provider == null || provider.isBlank() ? "openai" : provider;
            baseUrl = baseUrl == null ? "https://openrouter.ai/api/v1" : baseUrl;
            compileModel = compileModel == null ? "openai/gpt-5.4-nano" : compileModel;
            driftModel = driftModel == null ? "openai/gpt-5.4-nano" : driftModel;
            triageModel = triageModel == null ? "openai/gpt-5.4-nano" : triageModel;
            draftModel = draftModel == null ? "openai/gpt-5.4-nano" : draftModel;
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
        }

        @Override
        public @NonNull String toString() {
            return "PlatformProperties[provider="
                    + provider
                    + ", baseUrl="
                    + baseUrl
                    + ", apiKey=****, compileModel="
                    + compileModel
                    + ", driftModel="
                    + driftModel
                    + ", triageModel="
                    + triageModel
                    + ", draftModel="
                    + draftModel
                    + ", connectTimeout="
                    + connectTimeout
                    + ", readTimeout="
                    + readTimeout
                    + "]";
        }
    }

    public record ByokProperties(
            boolean allowNonVendorEndpoints,
            List<String> allowedExtraHosts,
            List<Integer> allowedExtraPorts,
            Duration connectTimeout,
            Duration readTimeout) {

        public static ByokProperties defaults() {
            return new ByokProperties(false, List.of(), List.of(), null, null);
        }

        public ByokProperties {
            allowedExtraHosts =
                    allowedExtraHosts == null ? List.of() : List.copyOf(allowedExtraHosts);
            allowedExtraPorts =
                    allowedExtraPorts == null ? List.of() : List.copyOf(allowedExtraPorts);
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(15) : readTimeout;
        }
    }

    @Override
    public @NonNull String toString() {
        return "LlmProperties[platform=" + platform + ", byok=" + byok + "]";
    }
}
