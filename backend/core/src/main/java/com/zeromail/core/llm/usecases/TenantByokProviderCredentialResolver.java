package com.zeromail.core.llm.usecases;

import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.llm.byok.ByokEndpointValidator;
import com.zeromail.core.llm.domain.BYOKProvider;
import com.zeromail.core.llm.persistence.TenantByokCredentialsEntity;
import com.zeromail.core.llm.persistence.TenantByokCredentialsRepository;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantByokProviderCredentialResolver {

    private static final String OPENAI_FORMAT = "OPENAI_FORMAT";
    private static final String ANTHROPIC_FORMAT = "ANTHROPIC_FORMAT";
    private static final String GOOGLE_FORMAT = "GOOGLE_FORMAT";

    private final TenantByokCredentialsRepository tenantByokCredentialsRepository;
    private final RefreshTokenCipher refreshTokenCipher;
    private final ByokEndpointValidator byokEndpointValidator;

    public TenantByokProviderCredentialResolver(
            TenantByokCredentialsRepository tenantByokCredentialsRepository,
            RefreshTokenCipher refreshTokenCipher,
            ByokEndpointValidator byokEndpointValidator) {
        this.tenantByokCredentialsRepository = tenantByokCredentialsRepository;
        this.refreshTokenCipher = refreshTokenCipher;
        this.byokEndpointValidator = byokEndpointValidator;
    }

    @Transactional(readOnly = true)
    public Optional<ResolvedLlmProviderCredential> resolve(UUID tenantId, String fallbackModel) {
        return tenantByokCredentialsRepository
                .findByTenantId(tenantId)
                .map(
                        credentials ->
                                toResolvedCredential(tenantId, credentials, fallbackModel, false));
    }

    @Transactional(readOnly = true)
    public Optional<ResolvedLlmProviderCredential> resolveForChat(
            UUID tenantId, String requestedModel) {
        return tenantByokCredentialsRepository
                .findByTenantId(tenantId)
                .map(
                        credentials ->
                                toResolvedCredential(tenantId, credentials, requestedModel, true));
    }

    private ResolvedLlmProviderCredential toResolvedCredential(
            UUID tenantId,
            TenantByokCredentialsEntity credentials,
            String fallbackModel,
            boolean preferFallbackModel) {
        BYOKProvider provider = credentials.getProvider();
        String model =
                modelFor(provider, credentials.getModel(), fallbackModel, preferFallbackModel);
        byte[] decryptedKey =
                refreshTokenCipher.decrypt(credentials.getEncryptedKey(), tenantId.toString());
        try {
            return new ResolvedLlmProviderCredential(
                    providerId(provider),
                    model,
                    new LlmProviderCredential(
                            providerId(provider),
                            keyFormat(provider),
                            endpointFor(provider, credentials.getEndpoint()),
                            decryptedKey,
                            LlmCredentialSource.BYOK),
                    credentials.getKeyVersion(),
                    1);
        } finally {
            Arrays.fill(decryptedKey, (byte) 0);
        }
    }

    private String endpointFor(BYOKProvider provider, String endpoint) {
        return switch (provider) {
            case ANTHROPIC ->
                    endpoint == null || endpoint.isBlank()
                            ? byokEndpointValidator.validateAnthropic(endpoint)
                            : byokEndpointValidator.validateAnthropicCompatible(endpoint);
            case DEEPSEEK -> byokEndpointValidator.validateDeepSeek(endpoint);
            case GOOGLE_GENAI -> byokEndpointValidator.validateGoogleGenAi(endpoint);
            case OPENAI -> byokEndpointValidator.validateOpenAiCompatible(endpoint);
        };
    }

    private static String providerId(BYOKProvider provider) {
        return switch (provider) {
            case ANTHROPIC -> "ANTHROPIC";
            case DEEPSEEK -> "DEEPSEEK";
            case GOOGLE_GENAI -> "GOOGLE";
            case OPENAI -> "OPENAI";
        };
    }

    private static String keyFormat(BYOKProvider provider) {
        return switch (provider) {
            case ANTHROPIC -> ANTHROPIC_FORMAT;
            case DEEPSEEK, OPENAI -> OPENAI_FORMAT;
            case GOOGLE_GENAI -> GOOGLE_FORMAT;
        };
    }

    private static String modelFor(
            BYOKProvider provider,
            String storedModel,
            String fallbackModel,
            boolean preferFallbackModel) {
        if (preferFallbackModel && fallbackModel != null && !fallbackModel.isBlank()) {
            return fallbackModel.trim();
        }
        if (storedModel != null && !storedModel.isBlank()) {
            return storedModel.trim();
        }
        if (fallbackModel != null && !fallbackModel.isBlank()) {
            return fallbackModel.trim();
        }
        return switch (provider) {
            case ANTHROPIC -> "claude-3-haiku-20240307";
            case DEEPSEEK -> "deepseek-chat";
            case GOOGLE_GENAI -> "gemini-2.0-flash";
            case OPENAI -> "gpt-4o-mini";
        };
    }
}
