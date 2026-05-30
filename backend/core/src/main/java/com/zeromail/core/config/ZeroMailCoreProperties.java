package com.zeromail.core.config;

import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Transitional LLM-only shell of the former config god-object (quick task w9t).
 *
 * <p>Task 3a extracted the crypto/gmail/billing/admin subtrees into per-feature records. This
 * record now holds ONLY the {@code llm} subtree; Task 3b relocates the LLM nested records into
 * {@code core.llm.config.LlmProperties} and deletes this class entirely.
 */
@ConfigurationProperties(prefix = "zero-mail")
@Validated
public record ZeroMailCoreProperties(@Valid @DefaultValue LlmProperties llm) {

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
            @jakarta.validation.constraints.NotBlank String apiKey,
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
            List<Integer> allowedExtraPorts,
            Duration connectTimeout,
            Duration readTimeout) {

        static ZeroMailLlmByokProperties defaults() {
            return new ZeroMailLlmByokProperties(false, List.of(), List.of(), null, null);
        }

        public ZeroMailLlmByokProperties {
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
        return "ZeroMailCoreProperties[llm=****]";
    }
}
