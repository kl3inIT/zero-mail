package com.zeromail.api.config;

import java.net.URI;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@ConfigurationProperties(prefix = "zeromail")
@Validated
public record ZeroMailApiProperties(
        @Valid WebProperties web,
        @Valid CorsProperties cors) {

    public ZeroMailApiProperties {
        web = web == null ? WebProperties.defaults() : web;
        cors = cors == null ? CorsProperties.defaults() : cors;
    }

    public record WebProperties(
            @DefaultValue("http://localhost:3000") @NotNull URI baseUrl) {

        static WebProperties defaults() {
            return new WebProperties(URI.create("http://localhost:3000"));
        }
    }

    public record CorsProperties(
            List<@NotBlank String> allowedOrigins,
            List<@NotBlank String> allowedOriginPatterns,
            List<@NotBlank String> allowedMethods,
            List<@NotBlank String> allowedHeaders,
            List<@NotBlank String> exposedHeaders,
            @DefaultValue("true") boolean allowCredentials,
            @DefaultValue("1800") @Min(0) long maxAge) {

        public CorsProperties {
            var normalizedPatterns = nonBlank(allowedOriginPatterns);
            var normalizedOrigins = nonBlank(allowedOrigins);
            if (normalizedOrigins.isEmpty() && normalizedPatterns.isEmpty()) {
                normalizedOrigins = List.of("http://localhost:3000");
            }
            allowedOrigins = normalizedOrigins;
            allowedOriginPatterns = normalizedPatterns;
            allowedMethods = defaultIfBlank(allowedMethods, List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            allowedHeaders = defaultIfBlank(allowedHeaders, List.of("*"));
            exposedHeaders = defaultIfBlank(exposedHeaders, List.of("XSRF-TOKEN"));
        }

        static CorsProperties defaults() {
            return new CorsProperties(null, null, null, null, null, true, 1800);
        }

        private static List<String> defaultIfBlank(List<String> values, List<String> fallback) {
            var normalized = nonBlank(values);
            return normalized.isEmpty() ? fallback : normalized;
        }

        private static List<String> nonBlank(List<String> values) {
            if (values == null) {
                return List.of();
            }
            return values.stream()
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
        }
    }
}
