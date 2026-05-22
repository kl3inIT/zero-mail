package com.zeromail.core.llm.gateway.springai.admin;

import com.zeromail.core.admin.mkey.domain.KeyFormat;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ModelsProbeClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient.Builder restClientBuilder;
    private final RestClient.Builder cleartextRestClientBuilder;
    private final ObjectMapper objectMapper;

    public ModelsProbeClient(
            RestClient.Builder restClientBuilder,
            @Qualifier("cleartextRestClientBuilder") RestClient.Builder cleartextRestClientBuilder,
            ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.cleartextRestClientBuilder = cleartextRestClientBuilder;
        this.objectMapper = objectMapper;
    }

    /**
     * Pick the HTTP client based on URL scheme. JDK HttpClient defaults to attempting h2c (HTTP/2
     * cleartext) upgrade on plain {@code http://} URLs, which hangs against Node.js / Next.js
     * backends that do not implement h2c (observed against 9router). Restrict cleartext targets to
     * HTTP/1.1; HTTPS keeps HTTP/2 via ALPN.
     */
    private RestClient.Builder builderFor(String resolvedBaseUrl) {
        return resolvedBaseUrl.regionMatches(true, 0, "http://", 0, 7)
                ? cleartextRestClientBuilder
                : restClientBuilder;
    }

    public MasterKeyTestResult probe(
            LlmProvider provider, KeyFormat keyFormat, String baseUrl, byte[] plaintextKey) {
        return probeConnection(provider, keyFormat, baseUrl, plaintextKey);
    }

    public MasterKeyTestResult probeConnection(
            LlmProvider provider, KeyFormat keyFormat, String baseUrl, byte[] plaintextKey) {
        String resolvedBaseUrl = baseUrlFor(provider, baseUrl);
        try {
            RestClient.RequestHeadersSpec<?> requestHeadersSpecification =
                    builderFor(resolvedBaseUrl)
                            .build()
                            .get()
                            .uri(joinPath(resolvedBaseUrl, "models"));
            String apiKey = new String(plaintextKey, StandardCharsets.UTF_8);
            applyHeaders(requestHeadersSpecification, provider, keyFormat, apiKey);
            requestHeadersSpecification.retrieve().toBodilessEntity();
            return withConstantJitter(MasterKeyTestResult.OK);
        } catch (RestClientResponseException providerRejection) {
            return withConstantJitter(mapStatus(providerRejection.getStatusCode().value()));
        } catch (ResourceAccessException resourceAccessException) {
            return withConstantJitter(
                    isTimeout(resourceAccessException)
                            ? MasterKeyTestResult.TIMEOUT
                            : MasterKeyTestResult.NETWORK_ERROR);
        } catch (RestClientException restClientException) {
            return withConstantJitter(MasterKeyTestResult.NETWORK_ERROR);
        }
    }

    public List<RawModel> fetchModelCatalog(
            LlmProvider provider, KeyFormat keyFormat, String baseUrl, byte[] plaintextKey) {
        String resolvedBaseUrl = baseUrlFor(provider, baseUrl);
        try {
            RestClient.RequestHeadersSpec<?> requestHeadersSpecification =
                    builderFor(resolvedBaseUrl)
                            .build()
                            .get()
                            .uri(joinPath(resolvedBaseUrl, "models"));
            String apiKey = new String(plaintextKey, StandardCharsets.UTF_8);
            applyHeaders(requestHeadersSpecification, provider, keyFormat, apiKey);
            String responseBody = requestHeadersSpecification.retrieve().body(String.class);
            if (responseBody == null || responseBody.isBlank()) {
                return List.of();
            }
            return parseModels(provider, responseBody);
        } catch (RestClientResponseException providerRejection) {
            throw new ProbeFailedException(mapStatus(providerRejection.getStatusCode().value()));
        } catch (ResourceAccessException resourceAccessException) {
            throw new ProbeFailedException(
                    isTimeout(resourceAccessException)
                            ? MasterKeyTestResult.TIMEOUT
                            : MasterKeyTestResult.NETWORK_ERROR);
        } catch (RestClientException restClientException) {
            throw new ProbeFailedException(MasterKeyTestResult.NETWORK_ERROR);
        }
    }

    private List<RawModel> parseModels(LlmProvider provider, String responseBody) {
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode modelsNode =
                    provider == LlmProvider.GOOGLE
                            ? rootNode.path("models")
                            : rootNode.path("data");
            if (!modelsNode.isArray()) {
                throw new ProbeFailedException(MasterKeyTestResult.NETWORK_ERROR);
            }
            List<RawModel> models = new ArrayList<>();
            for (JsonNode modelNode : modelsNode) {
                String modelId =
                        provider == LlmProvider.GOOGLE
                                ? googleModelId(modelNode)
                                : modelNode.path("id").asString();
                if (modelId == null || modelId.isBlank()) {
                    continue;
                }
                String displayName = optionalText(modelNode.path("display_name"));
                if (displayName == null) {
                    displayName = optionalText(modelNode.path("displayName"));
                }
                models.add(new RawModel(modelId, displayName));
            }
            return models;
        } catch (JacksonException jacksonException) {
            throw new ProbeFailedException(MasterKeyTestResult.NETWORK_ERROR);
        }
    }

    private static String googleModelId(JsonNode modelNode) {
        String name = modelNode.path("name").asString();
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.startsWith("models/") ? name.substring("models/".length()) : name;
    }

    private static String optionalText(JsonNode jsonNode) {
        if (jsonNode.isMissingNode() || jsonNode.isNull() || !jsonNode.isString()) {
            return null;
        }
        String value = jsonNode.asString();
        return value == null || value.isBlank() ? null : value;
    }

    private static void applyHeaders(
            RestClient.RequestHeadersSpec<?> requestHeadersSpecification,
            LlmProvider provider,
            KeyFormat keyFormat,
            String apiKey) {
        if (provider == LlmProvider.GOOGLE || keyFormat == KeyFormat.GOOGLE_FORMAT) {
            requestHeadersSpecification.header("x-goog-api-key", apiKey);
            return;
        }
        if (provider == LlmProvider.ANTHROPIC || keyFormat == KeyFormat.ANTHROPIC_FORMAT) {
            requestHeadersSpecification
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION);
            return;
        }
        requestHeadersSpecification.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
    }

    private static MasterKeyTestResult mapStatus(int status) {
        if (status == 401 || status == 403) {
            return MasterKeyTestResult.INVALID_KEY;
        }
        if (status == 429) {
            return MasterKeyTestResult.RATE_LIMITED;
        }
        if (status == 408) {
            return MasterKeyTestResult.TIMEOUT;
        }
        return MasterKeyTestResult.NETWORK_ERROR;
    }

    private static boolean isTimeout(Throwable throwable) {
        Throwable currentThrowable = throwable;
        while (currentThrowable != null) {
            if (currentThrowable instanceof SocketTimeoutException) {
                return true;
            }
            currentThrowable = currentThrowable.getCause();
        }
        return false;
    }

    private static String joinPath(String baseUrl, String suffix) {
        return baseUrl.replaceAll("/+$", "") + "/" + suffix.replaceAll("^/+", "");
    }

    private static String baseUrlFor(LlmProvider provider, String baseUrl) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl.trim();
        }
        String defaultBaseUrl = provider.defaultBaseUrl();
        if (defaultBaseUrl == null || defaultBaseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must be supplied for " + provider.id());
        }
        return defaultBaseUrl;
    }

    private static MasterKeyTestResult withConstantJitter(MasterKeyTestResult result) {
        try {
            Thread.sleep(50L);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
        return result;
    }

    public record RawModel(String modelId, String displayName) {}

    public static class ProbeFailedException extends RuntimeException {

        private final MasterKeyTestResult reason;

        public ProbeFailedException(MasterKeyTestResult reason) {
            super("Provider model catalog probe failed");
            this.reason = reason;
        }

        public MasterKeyTestResult reason() {
            return reason;
        }
    }
}
