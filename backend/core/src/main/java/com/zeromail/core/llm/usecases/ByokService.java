package com.zeromail.core.llm.usecases;

import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.llm.byok.ByokEndpointValidator;
import com.zeromail.core.llm.domain.BYOKProvider;
import com.zeromail.core.llm.domain.ByokProviderPreset;
import com.zeromail.core.llm.exception.InvalidByokException;
import com.zeromail.core.llm.gateway.springai.ByokValidationGateway;
import com.zeromail.core.llm.persistence.TenantByokCredentialsEntity;
import com.zeromail.core.llm.persistence.TenantByokCredentialsRepository;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ByokService {

    private static final Logger log = LoggerFactory.getLogger(ByokService.class);

    private final TenantByokCredentialsRepository tenantByokCredentialsRepository;
    private final RefreshTokenCipher refreshTokenCipher;
    private final ByokEndpointValidator byokEndpointValidator;
    private final ByokValidationGateway byokValidationGateway;

    public ByokService(
            TenantByokCredentialsRepository tenantByokCredentialsRepository,
            RefreshTokenCipher refreshTokenCipher,
            ByokEndpointValidator byokEndpointValidator,
            ByokValidationGateway byokValidationGateway) {
        this.tenantByokCredentialsRepository = tenantByokCredentialsRepository;
        this.refreshTokenCipher = refreshTokenCipher;
        this.byokEndpointValidator = byokEndpointValidator;
        this.byokValidationGateway = byokValidationGateway;
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
                probeUpstream(
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

    @Transactional(readOnly = true)
    public Optional<ByokChatCredential> chatCredential(UUID tenantId) {
        return tenantByokCredentialsRepository
                .findByTenantId(tenantId)
                .map(
                        credentials -> {
                            byte[] decryptedKey =
                                    refreshTokenCipher.decrypt(
                                            credentials.getEncryptedKey(), tenantId.toString());
                            try {
                                return new ByokChatCredential(
                                        credentials.getProvider(),
                                        credentials.getEndpoint(),
                                        credentials.getModel(),
                                        decryptedKey);
                            } finally {
                                Arrays.fill(decryptedKey, (byte) 0);
                            }
                        });
    }

    private ByokValidateResult probeUpstream(
            UUID tenantId,
            BYOKProvider provider,
            String canonicalEndpoint,
            String model,
            String apiKey) {
        try {
            return byokValidationGateway.validate(provider, canonicalEndpoint, model, apiKey);
        } catch (RuntimeException unexpectedFailure) {
            log.info(
                    "event=byok_validate_failed tenantId={} provider={} reason={}",
                    tenantId,
                    provider,
                    unexpectedFailure.getClass().getSimpleName());
            return new ByokValidateResult(false, null, "connection_failed");
        }
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

    private record ResolvedByokProvider(BYOKProvider provider, String canonicalEndpoint) {
        private ResolvedByokProvider {
            if (canonicalEndpoint == null || canonicalEndpoint.isBlank()) {
                throw new InvalidByokException();
            }
        }
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
}
