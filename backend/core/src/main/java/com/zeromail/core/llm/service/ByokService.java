package com.zeromail.core.llm.service;

import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.llm.application.ByokCurrent;
import com.zeromail.core.llm.application.ByokSaveCommand;
import com.zeromail.core.llm.application.ByokSaveResult;
import com.zeromail.core.llm.application.ByokValidateCommand;
import com.zeromail.core.llm.application.ByokValidateResult;
import com.zeromail.core.llm.byok.ByokEndpointValidator;
import com.zeromail.core.llm.domain.BYOKProvider;
import com.zeromail.core.llm.domain.ByokProviderPreset;
import com.zeromail.core.llm.exception.InvalidByokException;
import com.zeromail.core.llm.persistence.TenantByokCredentialsEntity;
import com.zeromail.core.llm.persistence.TenantByokCredentialsRepository;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class ByokService {

    private static final Logger log = LoggerFactory.getLogger(ByokService.class);
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final TenantByokCredentialsRepository tenantByokCredentialsRepository;
    private final RefreshTokenCipher refreshTokenCipher;
    private final ByokEndpointValidator byokEndpointValidator;
    private final RestClient.Builder restClientBuilder;

    public ByokService(
            TenantByokCredentialsRepository tenantByokCredentialsRepository,
            RefreshTokenCipher refreshTokenCipher,
            ByokEndpointValidator byokEndpointValidator,
            RestClient.Builder restClientBuilder) {
        this.tenantByokCredentialsRepository = tenantByokCredentialsRepository;
        this.refreshTokenCipher = refreshTokenCipher;
        this.byokEndpointValidator = byokEndpointValidator;
        this.restClientBuilder = restClientBuilder;
    }

    public ByokValidateResult validate(UUID tenantId, ByokValidateCommand command) {
        String model;
        try {
            model = canonicalModel(command.model());
        } catch (InvalidByokException invalidByokException) {
            log.info(
                    "event=byok_validate_failed tenantId={} preset={} reason=model_required",
                    tenantId,
                    command.preset());
            return new ByokValidateResult(false, null, "model_required");
        }

        ResolvedByokProvider resolvedProvider;
        try {
            resolvedProvider = resolveProvider(command.preset(), command.endpoint());
        } catch (InvalidByokException invalidByokException) {
            log.info(
                    "event=byok_validate_failed tenantId={} preset={} reason=endpoint_rejected",
                    tenantId,
                    command.preset());
            return new ByokValidateResult(false, null, "endpoint_rejected");
        }
        log.info(
                "event=byok_validate_attempted tenantId={} preset={} provider={}",
                tenantId,
                command.preset(),
                resolvedProvider.provider());
        ByokValidateResult result =
                probeUpstream(
                        tenantId,
                        resolvedProvider.provider(),
                        resolvedProvider.canonicalEndpoint(),
                        model,
                        command.apiKey());
        if (result.ok()) {
            int modelsCount = result.models() == null ? 0 : result.models().size();
            log.info(
                    "event=byok_validate_succeeded tenantId={} preset={} provider={} modelsCount={}",
                    tenantId,
                    command.preset(),
                    resolvedProvider.provider(),
                    modelsCount);
        } else {
            log.info(
                    "event=byok_validate_failed tenantId={} preset={} provider={} reason={}",
                    tenantId,
                    command.preset(),
                    resolvedProvider.provider(),
                    result.reason());
        }
        return result;
    }

    /**
     * Saves BYOK credentials only after server-side endpoint validation and an upstream key probe.
     * This intentionally re-runs the provider probe even if the browser already called validate, so
     * a direct POST to save cannot persist an unvalidated or revoked key.
     */
    @Transactional
    public ByokSaveResult save(UUID tenantId, ByokSaveCommand command) {
        ResolvedByokProvider resolvedProvider =
                resolveProvider(command.preset(), command.endpoint());
        String model = canonicalModel(command.model());
        ByokValidateResult upstreamValidation =
                validateUpstreamKey(
                        tenantId,
                        resolvedProvider.provider(),
                        resolvedProvider.canonicalEndpoint(),
                        model,
                        command.apiKey());
        if (!upstreamValidation.ok()) {
            throw new InvalidByokException();
        }

        byte[] plaintextKey = command.apiKey().getBytes(StandardCharsets.UTF_8);
        byte[] encryptedEnvelope;
        try {
            encryptedEnvelope = refreshTokenCipher.encrypt(plaintextKey, tenantId.toString());
        } finally {
            Arrays.fill(plaintextKey, (byte) 0);
        }
        short keyVersion = keyVersionFromEnvelope(encryptedEnvelope);
        TenantByokCredentialsEntity credentials =
                tenantByokCredentialsRepository
                        .findByTenantId(tenantId)
                        .map(
                                existingCredentials -> {
                                    existingCredentials.replaceCredential(
                                            resolvedProvider.provider(),
                                            resolvedProvider.canonicalEndpoint(),
                                            model,
                                            encryptedEnvelope,
                                            keyVersion);
                                    return existingCredentials;
                                })
                        .orElseGet(
                                () ->
                                        new TenantByokCredentialsEntity(
                                                UUID.randomUUID(),
                                                tenantId,
                                                resolvedProvider.provider(),
                                                resolvedProvider.canonicalEndpoint(),
                                                model,
                                                encryptedEnvelope,
                                                keyVersion));
        TenantByokCredentialsEntity savedCredentials =
                tenantByokCredentialsRepository.saveAndFlush(credentials);
        Instant savedAt =
                savedCredentials.getUpdatedAt() == null
                        ? Instant.now()
                        : savedCredentials.getUpdatedAt();
        return new ByokSaveResult(true, savedAt);
    }

    @Transactional(readOnly = true)
    public Optional<ByokCurrent> current(UUID tenantId) {
        return tenantByokCredentialsRepository
                .findByTenantId(tenantId)
                .map(
                        credentials ->
                                new ByokCurrent(
                                        credentials.getProvider(),
                                        endpointHost(credentials.getEndpoint()),
                                        credentials.getModel(),
                                        credentials.getUpdatedAt() == null
                                                ? credentials.getCreatedAt()
                                                : credentials.getUpdatedAt()));
    }

    private ByokValidateResult validateUpstreamKey(
            UUID tenantId,
            BYOKProvider provider,
            String canonicalEndpoint,
            String model,
            String apiKey) {
        return probeUpstream(tenantId, provider, canonicalEndpoint, model, apiKey);
    }

    private ByokValidateResult probeUpstream(
            UUID tenantId,
            BYOKProvider provider,
            String canonicalEndpoint,
            String model,
            String apiKey) {
        try {
            return switch (provider) {
                case ANTHROPIC -> probeAnthropic(canonicalEndpoint, apiKey, model);
                case DEEPSEEK -> probeDeepSeek(canonicalEndpoint, apiKey, model);
                case GOOGLE_GENAI -> probeGoogleGenAi(canonicalEndpoint, apiKey, model);
                case OPENAI -> probeOpenAi(canonicalEndpoint, apiKey, model);
            };
        } catch (RestClientResponseException upstreamRejection) {
            return new ByokValidateResult(
                    false, null, reasonForUpstreamRejection(provider, upstreamRejection));
        } catch (ResourceAccessException resourceAccessFailure) {
            return new ByokValidateResult(
                    false,
                    null,
                    isTimeout(resourceAccessFailure) ? "timeout" : "connection_failed");
        } catch (RestClientException restClientFailure) {
            return new ByokValidateResult(false, null, "connection_failed");
        } catch (RuntimeException unexpectedFailure) {
            log.info(
                    "event=byok_validate_failed tenantId={} provider={} reason={}",
                    tenantId,
                    provider,
                    unexpectedFailure.getClass().getSimpleName());
            return new ByokValidateResult(false, null, "connection_failed");
        }
    }

    private ByokValidateResult probeOpenAi(String canonicalEndpoint, String apiKey, String model) {
        ModelsResponse response =
                restClientBuilder
                        .build()
                        .get()
                        .uri(joinPath(canonicalEndpoint, "models"))
                        .headers(headers -> headers.setBearerAuth(apiKey))
                        .retrieve()
                        .body(ModelsResponse.class);
        List<String> modelIds =
                response == null || response.data() == null
                        ? List.of()
                        : response.data().stream().map(ModelResource::id).toList();
        if (!modelIds.isEmpty() && !modelIds.contains(model)) {
            return new ByokValidateResult(false, modelIds, "model_not_found");
        }
        return new ByokValidateResult(true, modelIds, null);
    }

    private ByokValidateResult probeDeepSeek(
            String canonicalEndpoint, String apiKey, String model) {
        return probeOpenAi(canonicalEndpoint, apiKey, model);
    }

    private ByokValidateResult probeAnthropic(
            String canonicalEndpoint, String apiKey, String model) {
        ModelsResponse response =
                restClientBuilder
                        .build()
                        .get()
                        .uri(joinPath(canonicalEndpoint, "models"))
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", ANTHROPIC_VERSION)
                        .retrieve()
                        .body(ModelsResponse.class);
        List<String> modelIds =
                response == null || response.data() == null
                        ? List.of()
                        : response.data().stream().map(ModelResource::id).toList();
        if (!modelIds.isEmpty() && !modelIds.contains(model)) {
            return new ByokValidateResult(false, modelIds, "model_not_found");
        }
        return new ByokValidateResult(true, modelIds.isEmpty() ? List.of(model) : modelIds, null);
    }

    private ByokValidateResult probeGoogleGenAi(
            String canonicalEndpoint, String apiKey, String model) {
        GoogleModelsResponse response =
                restClientBuilder
                        .build()
                        .get()
                        .uri(joinPath(canonicalEndpoint, "models"))
                        .header("x-goog-api-key", apiKey)
                        .retrieve()
                        .body(GoogleModelsResponse.class);
        List<String> modelIds =
                response == null || response.models() == null
                        ? List.of()
                        : response.models().stream()
                                .filter(ByokService::supportsGoogleGenerateContent)
                                .map(GoogleModelResource::name)
                                .filter(modelName -> modelName != null && !modelName.isBlank())
                                .map(ByokService::googleModelId)
                                .toList();
        if (!modelIds.isEmpty() && !modelIds.contains(model)) {
            return new ByokValidateResult(false, modelIds, "model_not_found");
        }
        return new ByokValidateResult(true, modelIds, null);
    }

    private ResolvedByokProvider resolveProvider(ByokProviderPreset preset, String endpoint) {
        String canonicalEndpoint =
                switch (preset) {
                    case OPENROUTER, OPENAI ->
                            byokEndpointValidator.validateOpenAi(preset.fixedEndpoint());
                    case ANTHROPIC ->
                            byokEndpointValidator.validateAnthropic(preset.fixedEndpoint());
                    case GOOGLE_GENAI ->
                            byokEndpointValidator.validateGoogleGenAi(preset.fixedEndpoint());
                    case DEEPSEEK -> byokEndpointValidator.validateDeepSeek(preset.fixedEndpoint());
                    case OPENAI_COMPATIBLE ->
                            byokEndpointValidator.validateCustomOpenAiCompatible(endpoint);
                    case ANTHROPIC_COMPATIBLE ->
                            byokEndpointValidator.validateAnthropicCompatible(endpoint);
                };
        return new ResolvedByokProvider(preset.provider(), canonicalEndpoint);
    }

    private String reasonForUpstreamRejection(
            BYOKProvider provider, RestClientResponseException upstreamRejection) {
        int upstreamStatus = upstreamRejection.getStatusCode().value();
        if (upstreamStatus == 404) {
            return "endpoint_rejected";
        }
        if (provider == BYOKProvider.ANTHROPIC && upstreamStatus == 400) {
            return "model_not_found";
        }
        if (upstreamStatus == 422) {
            return "model_not_found";
        }
        return "upstream_rejected";
    }

    private record ResolvedByokProvider(BYOKProvider provider, String canonicalEndpoint) {
        private ResolvedByokProvider {
            if (canonicalEndpoint == null || canonicalEndpoint.isBlank()) {
                throw new InvalidByokException();
            }
        }
    }

    private String joinPath(String canonicalEndpoint, String suffix) {
        return canonicalEndpoint.replaceAll("/+$", "") + "/" + suffix.replaceAll("^/+", "");
    }

    private static String canonicalModel(String model) {
        if (model == null || model.isBlank()) {
            throw new InvalidByokException();
        }
        return model.trim();
    }

    private static String endpointHost(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        return URI.create(endpoint).getHost();
    }

    private static short keyVersionFromEnvelope(byte[] encryptedEnvelope) {
        return (short) ByteBuffer.wrap(encryptedEnvelope).getInt();
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

    private record ModelsResponse(List<ModelResource> data) {}

    private record ModelResource(String id) {}

    private record GoogleModelsResponse(List<GoogleModelResource> models) {}

    private record GoogleModelResource(String name, List<String> supportedGenerationMethods) {}

    private static boolean supportsGoogleGenerateContent(GoogleModelResource modelResource) {
        return modelResource.supportedGenerationMethods() == null
                || modelResource.supportedGenerationMethods().contains("generateContent");
    }

    private static String googleModelId(String modelName) {
        return modelName.startsWith("models/")
                ? modelName.substring("models/".length())
                : modelName;
    }
}
