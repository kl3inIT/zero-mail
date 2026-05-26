package com.zeromail.core.llm.byok;

import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.usecases.MasterKeyTestResult;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.llm.gateway.springai.ConnectionTestResult;
import com.zeromail.core.llm.gateway.springai.ProviderConnectionTester;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class UserByokService {

    private static final int MAX_STORED_MODEL_IDS = 100;
    private static final String TEST_CONNECTION_RATE_LIMIT_KEY = "byok.test_connection";
    private static final int TEST_CONNECTION_MAX_PER_WINDOW = 10;
    private static final Duration TEST_CONNECTION_WINDOW = Duration.ofHours(1);

    private final UserByokKeyRepository userByokKeyRepository;
    private final ProviderAllowList providerAllowList;
    private final BaseUrlValidator baseUrlValidator;
    private final RefreshTokenCipher refreshTokenCipher;
    private final ProviderConnectionTester providerConnectionTester;
    private final ByokRateLimiter byokRateLimiter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public UserByokService(
            UserByokKeyRepository userByokKeyRepository,
            ProviderAllowList providerAllowList,
            BaseUrlValidator baseUrlValidator,
            RefreshTokenCipher refreshTokenCipher,
            ProviderConnectionTester providerConnectionTester,
            ByokRateLimiter byokRateLimiter,
            ObjectMapper objectMapper) {
        this(
                userByokKeyRepository,
                providerAllowList,
                baseUrlValidator,
                refreshTokenCipher,
                providerConnectionTester,
                byokRateLimiter,
                objectMapper,
                Clock.systemUTC());
    }

    UserByokService(
            UserByokKeyRepository userByokKeyRepository,
            ProviderAllowList providerAllowList,
            BaseUrlValidator baseUrlValidator,
            RefreshTokenCipher refreshTokenCipher,
            ProviderConnectionTester providerConnectionTester,
            ByokRateLimiter byokRateLimiter,
            ObjectMapper objectMapper,
            Clock clock) {
        this.userByokKeyRepository =
                Objects.requireNonNull(userByokKeyRepository, "userByokKeyRepository");
        this.providerAllowList = Objects.requireNonNull(providerAllowList, "providerAllowList");
        this.baseUrlValidator = Objects.requireNonNull(baseUrlValidator, "baseUrlValidator");
        this.refreshTokenCipher = Objects.requireNonNull(refreshTokenCipher, "refreshTokenCipher");
        this.providerConnectionTester =
                Objects.requireNonNull(providerConnectionTester, "providerConnectionTester");
        this.byokRateLimiter = Objects.requireNonNull(byokRateLimiter, "byokRateLimiter");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public ByokRowSummary save(UUID tenantId, ByokSaveCommand command) {
        LlmProvider provider = providerAllowList.validateForByok(command.provider());
        BaseUrlValidator.ValidatedTarget validatedTarget =
                baseUrlValidator.validate(command.baseUrl());
        requireProviderSupportsBaseUrl(provider, validatedTarget.uri().toString());

        byte[] plaintextKey = command.apiKey().getBytes(StandardCharsets.UTF_8);
        byte[] encryptedKey;
        try {
            encryptedKey = refreshTokenCipher.encrypt(plaintextKey, tenantId.toString());
        } finally {
            Arrays.fill(plaintextKey, (byte) 0);
        }

        Optional<UserByokKeyEntity> existingRow = userByokKeyRepository.findByTenantId(tenantId);
        UserByokKeyEntity userByokKey =
                existingRow.orElseGet(
                        () ->
                                new UserByokKeyEntity(
                                        tenantId,
                                        entityProvider(provider),
                                        validatedTarget.uri().toString(),
                                        encryptedKey,
                                        null));
        existingRow.ifPresent(
                existing ->
                        existing.replaceCredential(
                                entityProvider(provider),
                                validatedTarget.uri().toString(),
                                encryptedKey,
                                null));
        return toSummary(tenantId, userByokKeyRepository.save(userByokKey));
    }

    @Transactional(readOnly = true)
    public Optional<ByokRowSummary> load(UUID tenantId) {
        return userByokKeyRepository.findByTenantId(tenantId).map(row -> toSummary(tenantId, row));
    }

    @Transactional
    public ConnectionTestResult testConnection(UUID tenantId) {
        UserByokKeyEntity userByokKey =
                userByokKeyRepository.findByTenantId(tenantId).orElseThrow(ByokNoRowException::new);
        byokRateLimiter.requireAllowance(
                tenantId,
                TEST_CONNECTION_RATE_LIMIT_KEY,
                TEST_CONNECTION_MAX_PER_WINDOW,
                TEST_CONNECTION_WINDOW);

        BaseUrlValidator.ValidatedTarget validatedTarget =
                baseUrlValidator.validate(userByokKey.getBaseUrl());
        LlmProvider provider = LlmProvider.fromId(userByokKey.getProviderId());
        byte[] plaintextKey =
                refreshTokenCipher.decrypt(userByokKey.getApiKeyCiphertext(), tenantId.toString());
        try {
            ConnectionTestResult connectionTestResult =
                    providerConnectionTester.probeConnection(
                            provider, validatedTarget, plaintextKey);
            List<String> cappedModels =
                    connectionTestResult.result() == MasterKeyTestResult.OK
                            ? capModelIds(connectionTestResult.models())
                            : List.of();
            String modelsJson =
                    connectionTestResult.result() == MasterKeyTestResult.OK
                            ? writeModelsJson(cappedModels)
                            : null;
            userByokKey.recordConnectionTest(
                    entityLastTestResult(connectionTestResult.result()),
                    clock.instant(),
                    modelsJson);
            userByokKeyRepository.save(userByokKey);
            return new ConnectionTestResult(connectionTestResult.result(), cappedModels);
        } finally {
            Arrays.fill(plaintextKey, (byte) 0);
        }
    }

    @Transactional
    public ByokRowSummary setModel(UUID tenantId, String modelId) {
        UserByokKeyEntity userByokKey =
                userByokKeyRepository.findByTenantId(tenantId).orElseThrow(ByokNoRowException::new);
        String selectedModelId = requireText(modelId, "modelId");
        if (userByokKey.getLastTestResult() != UserByokKeyEntity.LastTestResult.OK
                || !storedModelIds(userByokKey).contains(selectedModelId)) {
            throw new ByokModelNotInLastTestException();
        }
        userByokKey.selectModel(selectedModelId);
        return toSummary(tenantId, userByokKeyRepository.save(userByokKey));
    }

    @Transactional
    public ByokRowSummary activate(UUID tenantId, boolean active) {
        UserByokKeyEntity userByokKey =
                userByokKeyRepository.findByTenantId(tenantId).orElseThrow(ByokNoRowException::new);
        if (active) {
            if (userByokKey.getModelId() == null
                    || userByokKey.getModelId().isBlank()
                    || userByokKey.getLastTestResult() != UserByokKeyEntity.LastTestResult.OK) {
                throw new ByokActivationGateException();
            }
            userByokKey.activate();
        } else {
            userByokKey.deactivate();
        }
        return toSummary(tenantId, userByokKeyRepository.save(userByokKey));
    }

    @Transactional
    public void delete(UUID tenantId) {
        userByokKeyRepository.deleteById(tenantId);
    }

    private ByokRowSummary toSummary(UUID tenantId, UserByokKeyEntity userByokKey) {
        return new ByokRowSummary(
                userByokKey.getProviderId(),
                userByokKey.getBaseUrl(),
                lastFourChars(tenantId, userByokKey),
                userByokKey.getModelId(),
                userByokKey.isActive(),
                userByokKey.getLastTestResultId(),
                userByokKey.getLastTestedAt(),
                storedModelIds(userByokKey));
    }

    private String lastFourChars(UUID tenantId, UserByokKeyEntity userByokKey) {
        byte[] plaintextKey =
                refreshTokenCipher.decrypt(userByokKey.getApiKeyCiphertext(), tenantId.toString());
        try {
            String apiKey = new String(plaintextKey, StandardCharsets.UTF_8);
            if (apiKey.length() <= 4) {
                return apiKey;
            }
            return apiKey.substring(apiKey.length() - 4);
        } finally {
            Arrays.fill(plaintextKey, (byte) 0);
        }
    }

    private List<String> storedModelIds(UserByokKeyEntity userByokKey) {
        String modelsJson = userByokKey.getLastTestModelsJson();
        if (modelsJson == null || modelsJson.isBlank()) {
            return List.of();
        }
        try {
            Object parsedValue = objectMapper.readValue(modelsJson, Object.class);
            if (!(parsedValue instanceof List<?> parsedValues)) {
                return List.of();
            }
            return parsedValues.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(storedModelId -> !storedModelId.isBlank())
                    .limit(MAX_STORED_MODEL_IDS)
                    .toList();
        } catch (JacksonException jacksonException) {
            return List.of();
        }
    }

    private String writeModelsJson(List<String> modelIds) {
        try {
            return objectMapper.writeValueAsString(capModelIds(modelIds));
        } catch (JacksonException jacksonException) {
            throw new IllegalStateException(
                    "Unable to serialize BYOK test models", jacksonException);
        }
    }

    private static List<String> capModelIds(List<String> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return List.of();
        }
        return modelIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(storedModelId -> !storedModelId.isBlank())
                .collect(
                        java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                                uniqueModelIds ->
                                        uniqueModelIds.stream()
                                                .limit(MAX_STORED_MODEL_IDS)
                                                .toList()));
    }

    private static void requireProviderSupportsBaseUrl(LlmProvider provider, String baseUrl) {
        if (!LlmProvider.GOOGLE.equals(provider)) {
            return;
        }
        if (!sameEndpoint(baseUrl, LlmProvider.defaultBaseUrl(provider))) {
            throw new BaseUrlNotSupportedForProviderException();
        }
    }

    private static boolean sameEndpoint(String candidateBaseUrl, String expectedBaseUrl) {
        return stripTrailingSlash(candidateBaseUrl).equals(stripTrailingSlash(expectedBaseUrl));
    }

    private static String stripTrailingSlash(String value) {
        String trimmedValue = value == null ? "" : value.trim();
        while (trimmedValue.endsWith("/")) {
            trimmedValue = trimmedValue.substring(0, trimmedValue.length() - 1);
        }
        return trimmedValue;
    }

    private static UserByokKeyEntity.Provider entityProvider(LlmProvider provider) {
        return UserByokKeyEntity.Provider.fromId(provider.id());
    }

    private static UserByokKeyEntity.LastTestResult entityLastTestResult(
            MasterKeyTestResult masterKeyTestResult) {
        return UserByokKeyEntity.LastTestResult.fromId(masterKeyTestResult.name());
    }

    private static String requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text.trim();
    }

    public static class ByokNoRowException extends BusinessException {

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.NOT_FOUND;
        }

        @Override
        public String errorCode() {
            return "ai.byok.no_row";
        }

        @Override
        public String logEvent() {
            return "byok_no_row";
        }

        @Override
        public String title() {
            return "BYOK key not found";
        }

        @Override
        public String detail() {
            return "No BYOK key is configured for this tenant.";
        }
    }

    public static class ByokActivationGateException extends BusinessException {

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.BAD_REQUEST;
        }

        @Override
        public String errorCode() {
            return "ai.byok.no_model_picked";
        }

        @Override
        public String logEvent() {
            return "byok_activation_gate_failed";
        }

        @Override
        public String title() {
            return "BYOK model not selected";
        }

        @Override
        public String detail() {
            return "BYOK must be tested successfully and have a selected model before activation.";
        }
    }

    public static class ByokModelNotInLastTestException extends BusinessException {

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.BAD_REQUEST;
        }

        @Override
        public String errorCode() {
            return "ai.byok.model_not_in_last_test";
        }

        @Override
        public String logEvent() {
            return "byok_model_not_in_last_test";
        }

        @Override
        public String title() {
            return "BYOK model unavailable";
        }

        @Override
        public String detail() {
            return "The selected model was not returned by the last successful BYOK test.";
        }
    }

    public static class BaseUrlNotSupportedForProviderException extends BusinessException {

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.BAD_REQUEST;
        }

        @Override
        public String errorCode() {
            return "ai.byok.base_url_not_supported_for_provider";
        }

        @Override
        public String logEvent() {
            return "byok_base_url_not_supported_for_provider";
        }

        @Override
        public String title() {
            return "BYOK base URL not supported";
        }

        @Override
        public String detail() {
            return "The selected provider does not support a custom BYOK base URL.";
        }
    }
}
