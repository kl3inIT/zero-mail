package com.zeromail.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "zero-mail.api")
@Validated
public record ApiProperties(
        @Valid WebProperties web, @Valid CorsProperties cors, @Valid GmailProperties gmail) {

    public ApiProperties {
        web = web == null ? WebProperties.defaults() : web;
        cors = cors == null ? CorsProperties.defaults() : cors;
        gmail = gmail == null ? GmailProperties.defaults() : gmail;
    }

    public record WebProperties(@DefaultValue("http://localhost:3000") @NotNull URI baseUrl) {

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
                normalizedOrigins = List.of("http://localhost:3000", "http://localhost:5174");
            }
            allowedOrigins = normalizedOrigins;
            allowedOriginPatterns = normalizedPatterns;
            allowedMethods =
                    defaultIfBlank(
                            allowedMethods,
                            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
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
            return values.stream().map(String::trim).filter(value -> !value.isEmpty()).toList();
        }
    }

    public record GmailProperties(@Valid PubSubProperties pubsub) {

        public GmailProperties {
            pubsub = pubsub == null ? PubSubProperties.defaults() : pubsub;
        }

        static GmailProperties defaults() {
            return new GmailProperties(null);
        }
    }

    public record PubSubProperties(
            @NotBlank String pushAudienceUrl,
            @NotBlank String saPrincipalEmail,
            @DefaultValue("https://www.googleapis.com/oauth2/v3/certs") @NotBlank
                    String oidcCertificatesUrl) {

        static PubSubProperties defaults() {
            return new PubSubProperties(null, null, "https://www.googleapis.com/oauth2/v3/certs");
        }
    }
}
