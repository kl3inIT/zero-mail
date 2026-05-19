package com.zeromail.core.admin.mkey.usecases;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import com.zeromail.core.admin.mkey.domain.KeyFormat;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.domain.MasterKeyFeature;
import com.zeromail.core.admin.mkey.domain.event.MasterKeyRotatedEvent;
import com.zeromail.core.admin.mkey.projection.MasterKeyMaskedRow;
import com.zeromail.core.llm.gateway.springai.admin.MasterKeyTestResult;
import com.zeromail.core.llm.gateway.springai.admin.ModelsProbeClient;
import com.zeromail.core.llm.gateway.springai.admin.ProviderMasterKeyResolver;
import com.zeromail.core.shared.crypto.PlatformSecretCipher;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasterKeyAdminService {

    private final JdbcTemplate jdbcTemplate;
    private final ProviderMasterKeyResolver providerMasterKeyResolver;
    private final PlatformSecretCipher platformSecretCipher;
    private final MasterKeyEditSessionService masterKeyEditSessionService;
    private final MasterKeyRateLimiter masterKeyRateLimiter;
    private final ModelsProbeClient modelsProbeClient;
    private final AdminAuditWriter adminAuditWriter;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public MasterKeyAdminService(
            JdbcTemplate jdbcTemplate,
            ProviderMasterKeyResolver providerMasterKeyResolver,
            PlatformSecretCipher platformSecretCipher,
            MasterKeyEditSessionService masterKeyEditSessionService,
            MasterKeyRateLimiter masterKeyRateLimiter,
            ModelsProbeClient modelsProbeClient,
            AdminAuditWriter adminAuditWriter,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.providerMasterKeyResolver =
                Objects.requireNonNull(providerMasterKeyResolver, "providerMasterKeyResolver");
        this.platformSecretCipher =
                Objects.requireNonNull(platformSecretCipher, "platformSecretCipher");
        this.masterKeyEditSessionService =
                Objects.requireNonNull(masterKeyEditSessionService, "masterKeyEditSessionService");
        this.masterKeyRateLimiter =
                Objects.requireNonNull(masterKeyRateLimiter, "masterKeyRateLimiter");
        this.modelsProbeClient = Objects.requireNonNull(modelsProbeClient, "modelsProbeClient");
        this.adminAuditWriter = Objects.requireNonNull(adminAuditWriter, "adminAuditWriter");
        this.applicationEventPublisher =
                Objects.requireNonNull(applicationEventPublisher, "applicationEventPublisher");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(readOnly = true)
    public List<MasterKeyMaskedRow> listMasked() {
        AdminContext.currentOrThrow();
        return providerMasterKeyResolver.maskedRows();
    }

    @Transactional(readOnly = true)
    public MasterKeyMaskedRow getMasked(LlmProvider provider) {
        AdminContext.currentOrThrow();
        return providerMasterKeyResolver.maskedRows().stream()
                .filter(masterKeyMaskedRow -> masterKeyMaskedRow.provider() == provider)
                .findFirst()
                .orElseThrow(() -> new MissingMasterKeyRowException(provider));
    }

    @Transactional
    public MasterKeyEditSessionService.EditSession mintEditSession(LlmProvider provider) {
        AdminUser adminUser = AdminContext.currentOrThrow();
        masterKeyRateLimiter.checkEditAllowed(adminUser.id());
        return masterKeyEditSessionService.mint(adminUser.id(), provider);
    }

    @Transactional
    public MasterKeyTestResult testConnection(
            LlmProvider provider,
            KeyFormat keyFormat,
            String baseUrl,
            byte[] plaintextKey,
            String editSessionToken,
            String requestIp,
            UUID requestId) {
        AdminUser adminUser = AdminContext.currentOrThrow();
        requireValidKeyFormat(provider, keyFormat);
        requireEditSession(adminUser.id(), provider, editSessionToken, false);
        masterKeyRateLimiter.checkTestConnectionAllowed(adminUser.id());
        MasterKeyTestResult result = probe(provider, keyFormat, baseUrl, plaintextKey);
        adminAuditWriter.append(
                AdminAuditAction.MASTER_KEY_TESTED,
                "llm_provider_master_key",
                null,
                null,
                "{\"provider\":\"" + provider.id() + "\",\"result_enum\":\"" + result + "\"}",
                "Master key connection tested",
                requestIp,
                requestId);
        return result;
    }

    @Transactional
    public MasterKeySetResult set(
            LlmProvider provider,
            KeyFormat keyFormat,
            String baseUrl,
            byte[] plaintextKey,
            String editSessionToken,
            String reason,
            String requestIp,
            UUID requestId) {
        AdminUser adminUser = AdminContext.currentOrThrow();
        requireValidKeyFormat(provider, keyFormat);
        requireEditSession(adminUser.id(), provider, editSessionToken, true);
        masterKeyRateLimiter.checkEditAllowed(adminUser.id());
        MasterKeyTestResult testResult = probe(provider, keyFormat, baseUrl, plaintextKey);
        if (testResult != MasterKeyTestResult.OK) {
            writeSetFailedAudit(provider, testResult, reason, requestIp, requestId);
            throw new MasterKeyTestFailedException(testResult);
        }
        String maskedKey = MasterKeyMasker.mask(plaintextKey, provider);
        StoredMasterKey storedMasterKey =
                storeMasterKey(provider, keyFormat, baseUrl, plaintextKey, adminUser.id());
        writeChangedAudit(
                AdminAuditAction.MASTER_KEY_SET,
                provider,
                keyFormat,
                storedMasterKey,
                maskedKey,
                reason,
                requestIp,
                requestId);
        applicationEventPublisher.publishEvent(
                new MasterKeyRotatedEvent(provider, storedMasterKey.providerSecretVersion()));
        return new MasterKeySetResult(storedMasterKey.providerSecretVersion(), testResult);
    }

    @Transactional
    public MasterKeyRotationResult rotate(
            LlmProvider provider,
            KeyFormat keyFormat,
            String baseUrl,
            byte[] plaintextKey,
            String editSessionToken,
            String reason,
            String requestIp,
            UUID requestId) {
        AdminUser adminUser = AdminContext.currentOrThrow();
        requireValidKeyFormat(provider, keyFormat);
        requireEditSession(adminUser.id(), provider, editSessionToken, true);
        masterKeyRateLimiter.checkEditAllowed(adminUser.id());
        MasterKeyTestResult testResult = probe(provider, keyFormat, baseUrl, plaintextKey);
        if (testResult != MasterKeyTestResult.OK) {
            adminAuditWriter.append(
                    AdminAuditAction.MASTER_KEY_ROTATION_FAILED,
                    "llm_provider_master_key",
                    null,
                    null,
                    "{\"provider\":\""
                            + provider.id()
                            + "\",\"key_format\":\""
                            + keyFormat.id()
                            + "\"}",
                    reason,
                    requestIp,
                    requestId);
            return new MasterKeyRotationResult("TEST_FAILED", testResult, null);
        }
        String maskedKey = MasterKeyMasker.mask(plaintextKey, provider);
        StoredMasterKey storedMasterKey =
                storeMasterKey(provider, keyFormat, baseUrl, plaintextKey, adminUser.id());
        writeChangedAudit(
                AdminAuditAction.MASTER_KEY_ROTATED,
                provider,
                keyFormat,
                storedMasterKey,
                maskedKey,
                reason,
                requestIp,
                requestId);
        applicationEventPublisher.publishEvent(
                new MasterKeyRotatedEvent(provider, storedMasterKey.providerSecretVersion()));
        return new MasterKeyRotationResult(
                "OK", testResult, storedMasterKey.providerSecretVersion());
    }

    @Transactional
    public void setFeatureDefault(
            MasterKeyFeature feature,
            LlmProvider provider,
            String reason,
            String requestIp,
            UUID requestId) {
        AdminContext.currentOrThrow();
        String columnName = feature.columnName();
        jdbcTemplate.update("UPDATE llm_provider_master_key SET " + columnName + " = FALSE");
        jdbcTemplate.update(
                "UPDATE llm_provider_master_key SET " + columnName + " = TRUE WHERE provider = ?",
                provider.id());
        adminAuditWriter.append(
                AdminAuditAction.MASTER_KEY_FEATURE_DEFAULT_SET,
                "llm_provider_master_key",
                null,
                null,
                "{\"provider\":\"" + provider.id() + "\",\"feature\":\"" + feature.id() + "\"}",
                reason,
                requestIp,
                requestId);
    }

    private StoredMasterKey storeMasterKey(
            LlmProvider provider,
            KeyFormat keyFormat,
            String baseUrl,
            byte[] plaintextKey,
            UUID actorId) {
        byte[] encryptedKey;
        try {
            encryptedKey =
                    platformSecretCipher.encrypt(
                            plaintextKey, ProviderMasterKeyResolver.associatedData(provider));
        } finally {
            Arrays.fill(plaintextKey, (byte) 0);
        }
        short kekVersion = PlatformSecretCipher.keyVersionFromEnvelope(encryptedKey);
        Instant now = clock.instant();
        Long providerSecretVersion =
                jdbcTemplate.queryForObject(
                        """
                        UPDATE llm_provider_master_key
                        SET key_format = ?,
                            encrypted_key = ?,
                            kek_version = ?,
                            provider_secret_version = provider_secret_version + 1,
                            created_by_user_id = COALESCE(created_by_user_id, ?),
                            last_rotated_at = ?,
                            base_url = ?
                        WHERE provider = ?
                        RETURNING provider_secret_version
                        """,
                        Long.class,
                        keyFormat.id(),
                        encryptedKey,
                        kekVersion,
                        actorId,
                        now,
                        cleanBaseUrl(baseUrl),
                        provider.id());
        if (providerSecretVersion == null) {
            throw new MissingMasterKeyRowException(provider);
        }
        providerMasterKeyResolver.invalidate(provider);
        return new StoredMasterKey(kekVersion, providerSecretVersion, now);
    }

    private MasterKeyTestResult probe(
            LlmProvider provider, KeyFormat keyFormat, String baseUrl, byte[] plaintextKey) {
        try {
            return modelsProbeClient.probe(
                    provider, keyFormat, cleanBaseUrl(baseUrl), plaintextKey);
        } finally {
            // The caller may still need the bytes for encryption; zeroing happens after successful
            // storage or in the controller for test-only requests.
        }
    }

    private void writeChangedAudit(
            AdminAuditAction action,
            LlmProvider provider,
            KeyFormat keyFormat,
            StoredMasterKey storedMasterKey,
            String maskedKey,
            String reason,
            String requestIp,
            UUID requestId) {
        adminAuditWriter.append(
                action,
                "llm_provider_master_key",
                null,
                null,
                "{\"masked_key\":\""
                        + maskedKey
                        + "\",\"kek_version\":"
                        + storedMasterKey.kekVersion()
                        + ",\"provider_secret_version\":"
                        + storedMasterKey.providerSecretVersion()
                        + ",\"last_rotated_at\":\""
                        + storedMasterKey.lastRotatedAt()
                        + "\",\"provider\":\""
                        + provider.id()
                        + "\",\"key_format\":\""
                        + keyFormat.id()
                        + "\"}",
                reason,
                requestIp,
                requestId);
    }

    private void writeSetFailedAudit(
            LlmProvider provider,
            MasterKeyTestResult testResult,
            String reason,
            String requestIp,
            UUID requestId) {
        adminAuditWriter.append(
                AdminAuditAction.MASTER_KEY_TESTED,
                "llm_provider_master_key",
                null,
                null,
                "{\"provider\":\"" + provider.id() + "\",\"result_enum\":\"" + testResult + "\"}",
                reason,
                requestIp,
                requestId);
    }

    private void requireEditSession(
            UUID actorId, LlmProvider provider, String editSessionToken, boolean consume) {
        boolean valid =
                consume
                        ? masterKeyEditSessionService
                                .consume(actorId, provider, editSessionToken)
                                .isPresent()
                        : masterKeyEditSessionService
                                .validate(actorId, provider, editSessionToken)
                                .isPresent();
        if (!valid) {
            throw new EditSessionRequiredException();
        }
    }

    private static void requireValidKeyFormat(LlmProvider provider, KeyFormat keyFormat) {
        if (keyFormat == null || !provider.acceptsKeyFormat(keyFormat)) {
            throw new InvalidKeyFormatException();
        }
    }

    private static String cleanBaseUrl(String baseUrl) {
        return baseUrl == null || baseUrl.isBlank() ? null : baseUrl.trim();
    }

    public record MasterKeySetResult(long providerSecretVersion, MasterKeyTestResult testResult) {}

    public record MasterKeyRotationResult(
            String result, MasterKeyTestResult testResult, Long providerSecretVersion) {}

    private record StoredMasterKey(
            short kekVersion, long providerSecretVersion, Instant lastRotatedAt) {}

    public static class EditSessionRequiredException extends RuntimeException {}

    public static class InvalidKeyFormatException extends RuntimeException {}

    public static class MissingMasterKeyRowException extends RuntimeException {

        public MissingMasterKeyRowException(LlmProvider provider) {
            super("Missing master key row for provider " + provider.id());
        }
    }

    public static class MasterKeyTestFailedException extends RuntimeException {

        private final MasterKeyTestResult result;

        public MasterKeyTestFailedException(MasterKeyTestResult result) {
            super("Master key test failed");
            this.result = result;
        }

        public MasterKeyTestResult result() {
            return result;
        }
    }
}
