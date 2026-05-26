package com.zeromail.core.llm.gateway.springai;

import com.zeromail.core.admin.mkey.domain.KeyFormat;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.usecases.MasterKeyTestResult;
import com.zeromail.core.llm.byok.BaseUrlValidator;
import com.zeromail.core.llm.byok.PinnedHttpClientFactory;
import com.zeromail.core.llm.byok.PinnedHttpClientFactory.PinnedHttpClient;
import com.zeromail.core.llm.byok.PinnedHttpClientFactory.PinnedHttpResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class ProviderConnectionTester {

    private static final int MODEL_LIMIT = 100;
    private static final List<String> NON_CHAT_MODEL_ID_MARKERS =
            List.of("embedding", "whisper", "tts", "dall-e", "text-embedding", "aqa");

    private final ModelsProbeClient modelsProbeClient;
    private final PinnedHttpClientFactory pinnedHttpClientFactory;

    public ProviderConnectionTester(
            ModelsProbeClient modelsProbeClient, PinnedHttpClientFactory pinnedHttpClientFactory) {
        this.modelsProbeClient = Objects.requireNonNull(modelsProbeClient, "modelsProbeClient");
        this.pinnedHttpClientFactory =
                Objects.requireNonNull(pinnedHttpClientFactory, "pinnedHttpClientFactory");
    }

    public ConnectionTestResult probeConnection(
            LlmProvider provider, String baseUrl, byte[] plaintextKey) {
        return probeConnection(
                provider, LlmProvider.defaultKeyFormat(provider), baseUrl, plaintextKey);
    }

    public ConnectionTestResult probeConnection(
            LlmProvider provider, KeyFormat keyFormat, String baseUrl, byte[] plaintextKey) {
        requireKeyFormat(provider, keyFormat);
        MasterKeyTestResult result =
                modelsProbeClient.probeConnection(provider, keyFormat, baseUrl, plaintextKey);
        if (result != MasterKeyTestResult.OK) {
            return new ConnectionTestResult(result, List.of());
        }
        return fetchModels(provider, keyFormat, baseUrl, plaintextKey);
    }

    public ConnectionTestResult probeConnection(
            LlmProvider provider,
            BaseUrlValidator.ValidatedTarget validatedTarget,
            byte[] plaintextKey) {
        KeyFormat keyFormat = LlmProvider.defaultKeyFormat(provider);
        requireKeyFormat(provider, keyFormat);
        PinnedHttpClient pinnedHttpClient = pinnedHttpClientFactory.clientFor(validatedTarget);
        Map<String, String> headers = headersFor(provider, keyFormat, plaintextKey);
        try {
            PinnedHttpResponse probeResponse = pinnedHttpClient.get("models", headers);
            MasterKeyTestResult probeResult = mapStatus(probeResponse.statusCode());
            if (probeResult != MasterKeyTestResult.OK) {
                return new ConnectionTestResult(probeResult, List.of());
            }
            PinnedHttpResponse catalogResponse = pinnedHttpClient.get("models", headers);
            MasterKeyTestResult catalogResult = mapStatus(catalogResponse.statusCode());
            if (catalogResult != MasterKeyTestResult.OK) {
                return new ConnectionTestResult(catalogResult, List.of());
            }
            List<String> models =
                    modelsProbeClient.parseModelCatalog(provider, catalogResponse.body()).stream()
                            .map(ModelsProbeClient.RawModel::modelId)
                            .filter(ProviderConnectionTester::isChatCapableModelId)
                            .limit(MODEL_LIMIT)
                            .toList();
            return new ConnectionTestResult(MasterKeyTestResult.OK, models);
        } catch (IOException | ModelsProbeClient.ProbeFailedException providerProbeFailure) {
            return new ConnectionTestResult(MasterKeyTestResult.NETWORK_ERROR, List.of());
        }
    }

    private ConnectionTestResult fetchModels(
            LlmProvider provider, KeyFormat keyFormat, String baseUrl, byte[] plaintextKey) {
        try {
            List<String> models =
                    modelsProbeClient
                            .fetchModelCatalog(provider, keyFormat, baseUrl, plaintextKey)
                            .stream()
                            .map(ModelsProbeClient.RawModel::modelId)
                            .filter(ProviderConnectionTester::isChatCapableModelId)
                            .limit(MODEL_LIMIT)
                            .toList();
            return new ConnectionTestResult(MasterKeyTestResult.OK, models);
        } catch (ModelsProbeClient.ProbeFailedException probeFailedException) {
            return new ConnectionTestResult(probeFailedException.reason(), List.of());
        }
    }

    private static Map<String, String> headersFor(
            LlmProvider provider, KeyFormat keyFormat, byte[] plaintextKey) {
        String apiKey = new String(plaintextKey, StandardCharsets.UTF_8);
        Map<String, String> headers = new LinkedHashMap<>();
        if (LlmProvider.GOOGLE.equals(provider) || keyFormat == KeyFormat.GOOGLE_FORMAT) {
            headers.put("x-goog-api-key", apiKey);
            return headers;
        }
        if (LlmProvider.ANTHROPIC.equals(provider) || keyFormat == KeyFormat.ANTHROPIC_FORMAT) {
            headers.put("x-api-key", apiKey);
            headers.put("anthropic-version", "2023-06-01");
            return headers;
        }
        headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        return headers;
    }

    private static MasterKeyTestResult mapStatus(int status) {
        if (status >= 200 && status < 300) {
            return MasterKeyTestResult.OK;
        }
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

    private static boolean isChatCapableModelId(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        String normalizedModelId = modelId.toLowerCase(Locale.ROOT);
        return NON_CHAT_MODEL_ID_MARKERS.stream().noneMatch(normalizedModelId::contains);
    }

    private static void requireKeyFormat(LlmProvider provider, KeyFormat keyFormat) {
        if (!LlmProvider.acceptsKeyFormat(provider, keyFormat)) {
            throw new IllegalArgumentException(
                    "Provider key format is not supported for " + provider.id());
        }
    }
}
