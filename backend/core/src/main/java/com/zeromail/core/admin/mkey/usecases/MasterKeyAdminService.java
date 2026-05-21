package com.zeromail.core.admin.mkey.usecases;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import com.zeromail.core.admin.mkey.domain.KeyFormat;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.domain.MasterKeyFeature;
import com.zeromail.core.admin.mkey.domain.MasterKeyStatus;
import com.zeromail.core.admin.mkey.domain.event.MasterKeyRotatedEvent;
import com.zeromail.core.admin.mkey.exception.EditSessionRequiredException;
import com.zeromail.core.admin.mkey.exception.InvalidKeyFormatException;
import com.zeromail.core.admin.mkey.exception.MasterKeyTestFailedException;
import com.zeromail.core.admin.mkey.exception.MissingMasterKeyRowException;
import com.zeromail.core.admin.mkey.exception.ProviderKeyReorderMismatchException;
import com.zeromail.core.admin.mkey.persistence.LlmProviderMasterKeyEntity;
import com.zeromail.core.admin.mkey.persistence.LlmProviderMasterKeyId;
import com.zeromail.core.admin.mkey.persistence.LlmProviderMasterKeyRepository;
import com.zeromail.core.admin.mkey.persistence.lowlevel.LlmProviderMasterKeyWriteRepository;
import com.zeromail.core.admin.mkey.projection.MasterKeyMaskedRow;
import com.zeromail.core.llm.gateway.springai.admin.MasterKeyTestResult;
import com.zeromail.core.llm.gateway.springai.admin.ModelsProbeClient;
import com.zeromail.core.llm.gateway.springai.admin.ProviderMasterKeyResolver;
import com.zeromail.core.shared.crypto.PlatformSecretCipher;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasterKeyAdminService {

    private final LlmProviderMasterKeyRepository llmProviderMasterKeyRepository;
    private final LlmProviderMasterKeyWriteRepository llmProviderMasterKeyWriteRepository;
    private final ProviderMasterKeyResolver providerMasterKeyResolver;
    private final PlatformSecretCipher platformSecretCipher;
    private final MasterKeyEditSessionService masterKeyEditSessionService;
    private final MasterKeyRateLimiter masterKeyRateLimiter;
    private final ModelsProbeClient modelsProbeClient;
    private final AdminAuditWriter adminAuditWriter;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public MasterKeyAdminService(
            LlmProviderMasterKeyRepository llmProviderMasterKeyRepository,
            LlmProviderMasterKeyWriteRepository llmProviderMasterKeyWriteRepository,
            ProviderMasterKeyResolver providerMasterKeyResolver,
            PlatformSecretCipher platformSecretCipher,
            MasterKeyEditSessionService masterKeyEditSessionService,
            MasterKeyRateLimiter masterKeyRateLimiter,
            ModelsProbeClient modelsProbeClient,
            AdminAuditWriter adminAuditWriter,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock) {
        this.llmProviderMasterKeyRepository =
                Objects.requireNonNull(
                        llmProviderMasterKeyRepository,
                        "llmProviderMasterKeyRepository must not be null");
        this.llmProviderMasterKeyWriteRepository =
                Objects.requireNonNull(
                        llmProviderMasterKeyWriteRepository,
                        "llmProviderMasterKeyWriteRepository must not be null");
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

    /**
     * Every credential row for a single provider, priority-ordered (lowest priority first).
     * Includes REVOKED rows so the admin can review the full failover chain.
     */
    @Transactional(readOnly = true)
    public List<LlmProviderMasterKeyEntity> listKeys(LlmProvider provider) {
        AdminContext.currentOrThrow();
        return llmProviderMasterKeyRepository.findByProviderOrderByPriority(provider);
    }

    /**
     * Inserts a new credential row into a provider's failover chain. The key is probed first; only
     * OK results are persisted. The new row's priority lands at the end of the existing ACTIVE
     * chain.
     */
    @Transactional
    public ProviderKeyAddResult addKey(
            LlmProvider provider,
            KeyFormat keyFormat,
            String baseUrl,
            byte[] plaintextKey,
            String label,
            String editSessionToken,
            String requestIp,
            UUID requestId) {
        AdminUser adminUser = AdminContext.currentOrThrow();
        requireValidKeyFormat(provider, keyFormat);
        requireEditSession(adminUser.id(), provider, editSessionToken, true);
        masterKeyRateLimiter.checkEditAllowed(adminUser.id());

        MasterKeyTestResult testResult = probe(provider, keyFormat, baseUrl, plaintextKey);
        if (testResult != MasterKeyTestResult.OK) {
            writeSetFailedAudit(provider, testResult, requestIp, requestId);
            throw new MasterKeyTestFailedException(testResult);
        }

        String maskedKey = MasterKeyMasker.mask(plaintextKey, provider);
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
        UUID keyId = UUID.randomUUID();

        int nextPriority =
                llmProviderMasterKeyRepository.findByProviderOrderByPriority(provider).stream()
                                .mapToInt(LlmProviderMasterKeyEntity::getPriority)
                                .max()
                                .orElse(0)
                        + 1;

        llmProviderMasterKeyWriteRepository.insertKey(
                provider,
                keyId,
                nextPriority,
                MasterKeyStatus.ACTIVE,
                label,
                keyFormat,
                encryptedKey,
                kekVersion,
                adminUser.id(),
                now,
                cleanBaseUrl(baseUrl),
                maskedKey);
        providerMasterKeyResolver.invalidate(provider);

        adminAuditWriter.append(
                AdminAuditAction.MASTER_KEY_SET,
                "llm_provider_master_key",
                null,
                null,
                Map.of(
                        "provider",
                        provider.id(),
                        "key_id",
                        keyId.toString(),
                        "priority",
                        nextPriority,
                        "label",
                        label == null ? "" : label),
                null,
                requestIp,
                requestId);
        return new ProviderKeyAddResult(keyId, nextPriority);
    }

    /**
     * Reorders priorities atomically within a provider. Caller supplies the full desired ordering
     * of key IDs; this method assigns sequential priorities 1..N.
     */
    @Transactional
    public void reorderKeys(
            LlmProvider provider, List<UUID> orderedKeyIds, String requestIp, UUID requestId) {
        AdminContext.currentOrThrow();
        Objects.requireNonNull(orderedKeyIds, "orderedKeyIds");

        List<LlmProviderMasterKeyEntity> existing =
                llmProviderMasterKeyRepository.findByProviderOrderByPriority(provider);
        if (existing.size() != orderedKeyIds.size()
                || !existing.stream()
                        .map(LlmProviderMasterKeyEntity::getKeyId)
                        .toList()
                        .containsAll(orderedKeyIds)) {
            throw new ProviderKeyReorderMismatchException(provider);
        }

        // Two-pass shift to dodge the deferrable uq_priority constraint.
        int offset = existing.size() + 1;
        for (LlmProviderMasterKeyEntity row : existing) {
            llmProviderMasterKeyWriteRepository.setPriority(
                    provider, row.getKeyId(), -(row.getPriority() + offset));
        }
        for (int index = 0; index < orderedKeyIds.size(); index++) {
            llmProviderMasterKeyWriteRepository.setPriority(
                    provider, orderedKeyIds.get(index), index + 1);
        }
        providerMasterKeyResolver.invalidate(provider);

        adminAuditWriter.append(
                AdminAuditAction.MASTER_KEY_FEATURE_DEFAULT_SET,
                "llm_provider_master_key",
                null,
                null,
                Map.of(
                        "provider", provider.id(),
                        "ordered_key_ids", orderedKeyIds.stream().map(UUID::toString).toList()),
                "Reordered failover priorities",
                requestIp,
                requestId);
    }

    /**
     * Probes a stored credential by decrypting it server-side, running it against the provider's
     * /models endpoint, and zeroing the buffer. Used by the admin detail view's per-row "Test"
     * button. Requires admin context but not an edit session (read-only test).
     */
    @Transactional
    public MasterKeyTestResult testKey(
            LlmProvider provider, UUID keyId, String requestIp, UUID requestId) {
        AdminUser adminUser = AdminContext.currentOrThrow();
        masterKeyRateLimiter.checkTestConnectionAllowed(adminUser.id());

        LlmProviderMasterKeyEntity row =
                llmProviderMasterKeyRepository
                        .findById(new LlmProviderMasterKeyId(provider, keyId))
                        .orElseThrow(() -> new MissingMasterKeyRowException(provider));

        byte[] plaintextKey =
                platformSecretCipher.decrypt(
                        row.getEncryptedKey(), ProviderMasterKeyResolver.associatedData(provider));
        MasterKeyTestResult result;
        try {
            result = probe(provider, row.getKeyFormat(), row.getBaseUrl(), plaintextKey);
        } finally {
            Arrays.fill(plaintextKey, (byte) 0);
        }

        adminAuditWriter.append(
                AdminAuditAction.MASTER_KEY_TESTED,
                "llm_provider_master_key",
                null,
                null,
                Map.of(
                        "provider", provider.id(),
                        "key_id", keyId.toString(),
                        "result_enum", result.name()),
                "Per-key connection test",
                requestIp,
                requestId);
        return result;
    }

    /** Marks a specific key row REVOKED. Idempotent. */
    @Transactional
    public void revokeKey(LlmProvider provider, UUID keyId, String requestIp, UUID requestId) {
        AdminContext.currentOrThrow();
        int rowsAffected = llmProviderMasterKeyWriteRepository.revokeKey(provider, keyId);
        if (rowsAffected == 0) {
            throw new MissingMasterKeyRowException(provider);
        }
        providerMasterKeyResolver.invalidate(provider);
        adminAuditWriter.append(
                AdminAuditAction.MASTER_KEY_ROTATED,
                "llm_provider_master_key",
                null,
                null,
                Map.of("provider", provider.id(), "key_id", keyId.toString(), "action", "REVOKE"),
                null,
                requestIp,
                requestId);
    }

    public record ProviderKeyAddResult(UUID keyId, int priority) {}

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
                Map.of("provider", provider.id(), "result_enum", result.name()),
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
            String requestIp,
            UUID requestId) {
        AdminUser adminUser = AdminContext.currentOrThrow();
        requireValidKeyFormat(provider, keyFormat);
        requireEditSession(adminUser.id(), provider, editSessionToken, true);
        masterKeyRateLimiter.checkEditAllowed(adminUser.id());
        MasterKeyTestResult testResult = probe(provider, keyFormat, baseUrl, plaintextKey);
        if (testResult != MasterKeyTestResult.OK) {
            writeSetFailedAudit(provider, testResult, requestIp, requestId);
            throw new MasterKeyTestFailedException(testResult);
        }
        String maskedKey = MasterKeyMasker.mask(plaintextKey, provider);
        StoredMasterKey storedMasterKey =
                storeMasterKey(
                        provider, keyFormat, baseUrl, plaintextKey, maskedKey, adminUser.id());
        writeChangedAudit(
                AdminAuditAction.MASTER_KEY_SET,
                provider,
                keyFormat,
                storedMasterKey,
                maskedKey,
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
                    Map.of("provider", provider.id(), "key_format", keyFormat.id()),
                    null,
                    requestIp,
                    requestId);
            return new MasterKeyRotationResult("TEST_FAILED", testResult, null);
        }
        String maskedKey = MasterKeyMasker.mask(plaintextKey, provider);
        StoredMasterKey storedMasterKey =
                storeMasterKey(
                        provider, keyFormat, baseUrl, plaintextKey, maskedKey, adminUser.id());
        writeChangedAudit(
                AdminAuditAction.MASTER_KEY_ROTATED,
                provider,
                keyFormat,
                storedMasterKey,
                maskedKey,
                requestIp,
                requestId);
        applicationEventPublisher.publishEvent(
                new MasterKeyRotatedEvent(provider, storedMasterKey.providerSecretVersion()));
        return new MasterKeyRotationResult(
                "OK", testResult, storedMasterKey.providerSecretVersion());
    }

    /**
     * Phase B v2 deprecates the boolean-column feature-default flow that the v1 admin UI drove
     * through this method. The new admin surface writes to {@code feature_default_provider} via the
     * tier matrix endpoints instead, so this method now throws — callers must migrate. Kept on the
     * controller to surface a meaningful 410 response until the v2 routes ship.
     */
    @Transactional
    public void setFeatureDefault(
            MasterKeyFeature feature, LlmProvider provider, String requestIp, UUID requestId) {
        AdminContext.currentOrThrow();
        throw new UnsupportedOperationException(
                "Legacy boolean-column feature-default flow removed in Phase B v2. "
                        + "Use the tier matrix endpoint to set feature defaults.");
    }

    /**
     * Upserts the canonical (priority=1) key row for a provider. Bridges the v1 "one canonical key
     * per provider" admin contract onto the v2 multi-key schema by:
     *
     * <ul>
     *   <li>looking up an existing priority=1 row (regardless of status),
     *   <li>REPLACEing its encrypted material + bumping {@code provider_secret_version} when found,
     *   <li>INSERTing a fresh ACTIVE priority=1 row when no row exists.
     * </ul>
     */
    private StoredMasterKey storeMasterKey(
            LlmProvider provider,
            KeyFormat keyFormat,
            String baseUrl,
            byte[] plaintextKey,
            String maskedKey,
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
        String cleanedBaseUrl = cleanBaseUrl(baseUrl);

        LlmProviderMasterKeyEntity existingPrimary =
                llmProviderMasterKeyRepository.findByProviderOrderByPriority(provider).stream()
                        .filter(entity -> entity.getPriority() == 1)
                        .findFirst()
                        .orElse(null);

        long providerSecretVersion;
        if (existingPrimary != null) {
            providerSecretVersion =
                    llmProviderMasterKeyWriteRepository.replaceKeyAndReturnVersion(
                            provider,
                            existingPrimary.getKeyId(),
                            keyFormat,
                            encryptedKey,
                            kekVersion,
                            actorId,
                            now,
                            cleanedBaseUrl,
                            maskedKey);
        } else {
            UUID newKeyId = UUID.randomUUID();
            llmProviderMasterKeyWriteRepository.insertKey(
                    provider,
                    newKeyId,
                    1,
                    MasterKeyStatus.ACTIVE,
                    "primary",
                    keyFormat,
                    encryptedKey,
                    kekVersion,
                    actorId,
                    now,
                    cleanedBaseUrl,
                    maskedKey);
            providerSecretVersion = 1L;
        }
        providerMasterKeyResolver.invalidate(provider);
        return new StoredMasterKey(kekVersion, providerSecretVersion, now);
    }

    /**
     * Runs the connectivity probe for the supplied master-key candidate. Ownership note: the caller
     * retains ownership of {@code plaintextKey}. This method does NOT zero the buffer — test-only
     * callers (the controller's {@code test-connection} endpoint) zero after returning, and write
     * callers ({@link #set}, {@link #rotate}) flow the same buffer into {@link #storeMasterKey}
     * which zeros it in its own {@code finally}.
     */
    private MasterKeyTestResult probe(
            LlmProvider provider, KeyFormat keyFormat, String baseUrl, byte[] plaintextKey) {
        return modelsProbeClient.probe(provider, keyFormat, cleanBaseUrl(baseUrl), plaintextKey);
    }

    private void writeChangedAudit(
            AdminAuditAction action,
            LlmProvider provider,
            KeyFormat keyFormat,
            StoredMasterKey storedMasterKey,
            String maskedKey,
            String requestIp,
            UUID requestId) {
        Map<String, Object> afterState = new java.util.LinkedHashMap<>();
        afterState.put("masked_key", maskedKey);
        afterState.put("kek_version", storedMasterKey.kekVersion());
        afterState.put("provider_secret_version", storedMasterKey.providerSecretVersion());
        afterState.put(
                "last_rotated_at",
                storedMasterKey.lastRotatedAt() == null
                        ? null
                        : storedMasterKey.lastRotatedAt().toString());
        afterState.put("provider", provider.id());
        afterState.put("key_format", keyFormat.id());
        adminAuditWriter.append(
                action,
                "llm_provider_master_key",
                null,
                null,
                afterState,
                null,
                requestIp,
                requestId);
    }

    private void writeSetFailedAudit(
            LlmProvider provider,
            MasterKeyTestResult testResult,
            String requestIp,
            UUID requestId) {
        // REQUIRES_NEW so the audit row survives the rollback caused by
        // MasterKeyTestFailedException thrown right after this call. Mirrors the
        // "every test/set/rotate attempt is auditable" R-8B invariant.
        adminAuditWriter.appendInNewTransaction(
                AdminAuditAction.MASTER_KEY_TESTED,
                "llm_provider_master_key",
                null,
                null,
                Map.of("provider", provider.id(), "result_enum", testResult.name()),
                null,
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
}
