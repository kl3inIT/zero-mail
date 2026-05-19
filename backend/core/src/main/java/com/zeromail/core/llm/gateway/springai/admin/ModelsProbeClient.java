package com.zeromail.core.llm.gateway.springai.admin;

import com.zeromail.core.admin.mkey.domain.KeyFormat;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ModelsProbeClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient.Builder restClientBuilder;

    public ModelsProbeClient(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    public MasterKeyTestResult probe(
            LlmProvider provider, KeyFormat keyFormat, String baseUrl, byte[] plaintextKey) {
        try {
            RestClient.RequestHeadersSpec<?> requestHeadersSpecification =
                    restClientBuilder
                            .build()
                            .get()
                            .uri(joinPath(baseUrlFor(provider, baseUrl), "models"));
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
}
