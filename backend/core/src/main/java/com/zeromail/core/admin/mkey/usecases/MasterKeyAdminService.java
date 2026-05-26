package com.zeromail.core.admin.mkey.usecases;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import com.zeromail.core.admin.cat.persistence.lowlevel.ProviderCatalogWriteRepository;
import com.zeromail.core.admin.cat.persistence.lowlevel.ProviderCatalogWriteRepository.ProviderDeleteCandidate;
import com.zeromail.core.admin.cat.persistence.lowlevel.ProviderCatalogWriteRepository.ProviderDeleteResult;
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
import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.llm.gateway.springai.ProviderConnectionTester;
import com.zeromail.core.shared.crypto.PlatformSecretCipher;
import com.zeromail.core.shared.exception.ErrorClass;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
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
    private final ProviderCatalogWriteRepository providerCatalogWriteRepository;
    private final ProviderMasterKeyResolver providerMasterKeyResolver;
    private final PlatformSecretCipher platformSecretCipher;
    private final MasterKeyEditSessionService masterKeyEditSessionService;
    private final MasterKeyRateLimiter masterKeyRateLimiter;
    private final ProviderConnectionTester providerConnectionTester;
    private final AdminAuditWriter adminAuditWriter;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public MasterKeyAdminService(
            LlmProviderMasterKeyRepository llmProviderMasterKeyRepository,
            LlmProviderMasterKeyWriteRepository llmProviderMasterKeyWriteRepository,
            ProviderCatalogWriteRepository providerCatalogWriteRepository,
            ProviderMasterKeyResolver providerMasterKeyResolver,
            PlatformSecretCipher platformSecretCipher,
            MasterKeyEditSessionService masterKeyEditSessionService,
            MasterKeyRateLimiter masterKeyRateLimiter,
            ProviderConnectionTester providerConnectionTester,
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
        this.providerCatalogWriteRepository =
                Objects.requireNonNull(
                        providerCatalogWriteRepository, "providerCatalogWriteRepository");
        this.providerMasterKeyResolver =
                Objects.requireNonNull(providerMasterKeyResolver, "providerMasterKeyResolver");
        this.platformSecretCipher =
                Objects.requireNonNull(platformSecretCipher, "platformSecretCipher");
        this.masterKeyEditSessionService =
                Objects.requireNonNull(masterKeyEditSessionService, "masterKeyEditSessionService");
        this.masterKeyRateLimiter =
                Objects.requireNonNull(masterKeyRateLimiter, "masterKeyRateLimiter");
        this.providerConnectionTester =
                Objects.requireNonNull(providerConnectionTester, "providerConnectionTester");
        this.adminAuditWriter = Objects.requireNonNull(adminAuditWriter, "adminAuditWriter");
        this.applicationEventPublisher =
                Objects.requireNonNull(applicationEventPublisher, "applicationEventPublisher");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(readOnly = true)
    public List<MasterKeyMaskedRow> listMasked() {
        AdminContext.currentOrThrow();
        // Provider list page wants ONE card per provider. The resolver returns one row per
        // (provider, key) — dedupe by provider, preferring rows with a populated masked-key
        // snippet (i.e. with stored key material — typically ACTIVE/PENDING) over rows whose
        // masked_key is null (legacy pre-080 rows, or REVOKED rows persisted before the mask
        // column was backfilled). The card's purpose is to surface a usable credential when one
        // exists.
        Map<LlmProvider, MasterKeyMaskedRow> byProvider = new LinkedHashMap<>();
        Map<LlmProvider, Long> activeKeyCountByProvider = new LinkedHashMap<>();
        for (MasterKeyMaskedRow row : providerMasterKeyResolver.maskedRows()) {
            activeKeyCountByProvider.merge(row.provider(), row.activeKeyCount(), Long::sum);
            MasterKeyMaskedRow existing = byProvider.get(row.provider());
            if (existing == null) {
                byProvider.put(row.provider(), row);
                continue;
            }
            // Prefer a row with a populated masked-key snippet (i.e. has encrypted material).
            if (row.maskedKey() != null && existing.maskedKey() == null) {
                byProvider.put(row.provider(), row);
            }
        }
        return byProvider.values().stream()
                .map(
                        row ->
                                withActiveKeyCount(
                                        row,
                                        activeKeyCountByProvider.getOrDefault(
                                                row.provider(), row.activeKeyCount())))
                .toList();
    }

    private static MasterKeyMaskedRow withActiveKeyCount(
            MasterKeyMaskedRow row, long activeKeyCount) {
        return new MasterKeyMaskedRow(
                row.provider(),
                row.displayName(),
                row.providerKind(),
                row.compatibleType(),
                row.defaultBaseUrl(),
                row.maskedKey(),
                row.keyFormat(),
                row.kekVersion(),
                row.providerSecretVersion(),
                row.lastRotatedAt(),
                row.dependentsCount(),
                activeKeyCount,
                row.rotationRecommended(),
                row.baseUrl(),
                row.featureDefaultProviderChat(),
                row.featureDefaultProviderTriage(),
                row.featureDefaultProviderDraft());
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
     * Creates a DB-defined compatibility provider and its first ACTIVE key in one transaction. The
     * key is probed before any row is persisted; the create API still re-runs this even when the UI
     * already performed the explicit "Test connection" step, so direct API callers cannot bypass
     * the safety gate.
     */
    @Transactional
    public ProviderKeyAddResult createCompatibleProvider(
            String providerId,
            String displayName,
            KeyFormat compatibleType,
            String defaultBaseUrl,
            byte[] plaintextKey,
            String label,
            String editSessionToken,
            String requestIp,
            UUID requestId) {
        AdminUser adminUser = AdminContext.currentOrThrow();
        LlmProvider provider = LlmProvider.fromId(providerId);
        requireNewProvider(provider);
        requireCompatibleGatewayFormat(compatibleType);
        String cleanedDefaultBaseUrl = requireValidBaseUrl(defaultBaseUrl);
        String cleanedDisplayName = requireDisplayName(displayName);
        requireEditSession(adminUser.id(), provider, editSessionToken, true);
        masterKeyRateLimiter.checkEditAllowed(adminUser.id());

        MasterKeyTestResult testResult =
                probe(provider, compatibleType, cleanedDefaultBaseUrl, plaintextKey);
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

        providerCatalogWriteRepository.insertCompatibleGateway(
                provider, cleanedDisplayName, compatibleType, cleanedDefaultBaseUrl);
        llmProviderMasterKeyWriteRepository.insertKey(
                provider,
                keyId,
                1,
                MasterKeyStatus.ACTIVE,
                cleanLabel(label, "primary"),
                compatibleType,
                encryptedKey,
                kekVersion,
                adminUser.id(),
                now,
                cleanedDefaultBaseUrl,
                maskedKey);
        providerMasterKeyResolver.invalidate(provider);

        adminAuditWriter.append(
                AdminAuditAction.MASTER_KEY_SET,
                "provider_catalog",
                null,
                null,
                Map.of(
                        "provider",
                        provider.id(),
                        "display_name",
                        cleanedDisplayName,
                        "provider_kind",
                        ProviderCatalogWriteRepository.KIND_COMPATIBLE_GATEWAY,
                        "compatible_type",
                        compatibleType.id(),
                        "key_id",
                        keyId.toString()),
                "Created compatible LLM provider",
                requestIp,
                requestId);
        return new ProviderKeyAddResult(keyId, 1);
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

        // Two-pass shift to dodge the deferrable uq_priority constraint. ck_priority_positive
        // is NOT deferrable, so the intermediate values stay above the new max (offset start)
        // instead of going negative.
        int offset = existing.size() + 100;
        for (LlmProviderMasterKeyEntity row : existing) {
            llmProviderMasterKeyWriteRepository.setPriority(
                    provider, row.getKeyId(), row.getPriority() + offset);
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

    /**
     * Patches operator-facing metadata (label, baseUrl) on an existing key row. Does not touch the
     * encrypted material, priority, or status.
     */
    @Transactional
    public void updateKey(
            LlmProvider provider,
            UUID keyId,
            String label,
            String baseUrl,
            String requestIp,
            UUID requestId) {
        AdminContext.currentOrThrow();
        int rowsAffected =
                llmProviderMasterKeyWriteRepository.updateLabelAndBaseUrl(
                        provider, keyId, label, cleanBaseUrl(baseUrl));
        if (rowsAffected == 0) {
            throw new MissingMasterKeyRowException(provider);
        }
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
                        "label",
                        label == null ? "" : label,
                        "base_url",
                        baseUrl == null ? "" : baseUrl,
                        "action",
                        "PATCH_METADATA"),
                "Patched provider key metadata",
                requestIp,
                requestId);
    }

    /** Deletes a specific provider key row. Audit keeps the append-only operator trail. */
    @Transactional
    public void revokeKey(LlmProvider provider, UUID keyId, String requestIp, UUID requestId) {
        AdminContext.currentOrThrow();
        int rowsAffected = llmProviderMasterKeyWriteRepository.deleteKey(provider, keyId);
        if (rowsAffected == 0) {
            throw new MissingMasterKeyRowException(provider);
        }
        providerMasterKeyResolver.invalidate(provider);
        adminAuditWriter.append(
                AdminAuditAction.MASTER_KEY_ROTATED,
                "llm_provider_master_key",
                null,
                null,
                Map.of("provider", provider.id(), "key_id", keyId.toString(), "action", "DELETE"),
                null,
                requestIp,
                requestId);
    }

    /** Deletes an admin-created compatibility provider and its stored keys/models when unused. */
    @Transactional
    public void deleteCompatibleProvider(LlmProvider provider, String requestIp, UUID requestId) {
        AdminContext.currentOrThrow();
        ProviderDeleteCandidate candidate =
                providerCatalogWriteRepository.findDeleteCandidateOrNull(provider);
        if (candidate == null) {
            throw new ProviderNotFoundException(provider);
        }
        if (!ProviderCatalogWriteRepository.KIND_COMPATIBLE_GATEWAY.equals(
                candidate.providerKind())) {
            throw new ProviderDeleteNotAllowedException(provider);
        }

        long routingReferenceCount =
                providerCatalogWriteRepository.countRoutingReferences(provider);
        long pinnedTenantCount = providerCatalogWriteRepository.countPinnedTenants(provider);
        if (routingReferenceCount > 0 || pinnedTenantCount > 0) {
            throw new ProviderDeleteBlockedException(
                    provider, routingReferenceCount, pinnedTenantCount);
        }

        ProviderDeleteResult result =
                providerCatalogWriteRepository.deleteCompatibleGateway(provider);
        if (result.deletedProviders() == 0) {
            throw new ProviderNotFoundException(provider);
        }
        providerMasterKeyResolver.invalidate(provider);

        adminAuditWriter.append(
                AdminAuditAction.MASTER_KEY_SET,
                "provider_catalog",
                null,
                null,
                Map.of(
                        "provider",
                        provider.id(),
                        "action",
                        "DELETE_COMPATIBLE_PROVIDER",
                        "deleted_models",
                        result.deletedModels(),
                        "deleted_keys",
                        result.deletedKeys()),
                "Deleted compatible LLM provider",
                requestIp,
                requestId);
    }

    public record ProviderKeyAddResult(UUID keyId, int priority) {}

    @Transactional(readOnly = true)
    public MasterKeyMaskedRow getMasked(LlmProvider provider) {
        AdminContext.currentOrThrow();
        return providerMasterKeyResolver.maskedRows().stream()
                .filter(masterKeyMaskedRow -> masterKeyMaskedRow.provider().equals(provider))
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
        return providerConnectionTester
                .probeConnection(provider, keyFormat, cleanBaseUrl(baseUrl), plaintextKey)
                .result();
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

    private void requireNewProvider(LlmProvider provider) {
        if (LlmProvider.isSpringAiBuiltIn(provider)
                || providerCatalogWriteRepository.exists(provider)) {
            throw new ProviderAlreadyExistsException(provider);
        }
    }

    private static void requireCompatibleGatewayFormat(KeyFormat compatibleType) {
        if (compatibleType != KeyFormat.OPENAI_FORMAT
                && compatibleType != KeyFormat.ANTHROPIC_FORMAT) {
            throw new InvalidKeyFormatException();
        }
    }

    private static String requireValidBaseUrl(String baseUrl) {
        String cleanedBaseUrl = cleanBaseUrl(baseUrl);
        if (cleanedBaseUrl == null) {
            throw new ProviderBaseUrlInvalidException();
        }
        try {
            URI uri = new URI(cleanedBaseUrl);
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))
                    || uri.getHost() == null
                    || uri.getHost().isBlank()) {
                throw new ProviderBaseUrlInvalidException();
            }
            return cleanedBaseUrl;
        } catch (URISyntaxException uriSyntaxException) {
            throw new ProviderBaseUrlInvalidException();
        }
    }

    private static String requireDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new ProviderDisplayNameInvalidException();
        }
        return displayName.trim();
    }

    private static String cleanLabel(String label, String fallback) {
        String cleanedLabel = label == null || label.isBlank() ? fallback : label.trim();
        return cleanedLabel.length() > 64 ? cleanedLabel.substring(0, 64) : cleanedLabel;
    }

    private static String cleanBaseUrl(String baseUrl) {
        return baseUrl == null || baseUrl.isBlank() ? null : baseUrl.trim();
    }

    public record MasterKeySetResult(long providerSecretVersion, MasterKeyTestResult testResult) {}

    public record MasterKeyRotationResult(
            String result, MasterKeyTestResult testResult, Long providerSecretVersion) {}

    private record StoredMasterKey(
            short kekVersion, long providerSecretVersion, Instant lastRotatedAt) {}

    public static class ProviderAlreadyExistsException extends AdminBusinessException {

        private final LlmProvider provider;

        public ProviderAlreadyExistsException(LlmProvider provider) {
            super("LLM provider already exists");
            this.provider = provider;
        }

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.CONFLICT;
        }

        @Override
        public String errorCode() {
            return "error.admin.llm_provider_already_exists";
        }

        @Override
        public String logEvent() {
            return "admin_llm_provider_already_exists";
        }

        @Override
        public String detail() {
            return "The provider already exists in the LLM provider catalog.";
        }

        @Override
        public Map<String, Object> params() {
            return Map.of("provider", provider.id());
        }
    }

    public static class ProviderNotFoundException extends AdminBusinessException {

        private final LlmProvider provider;

        public ProviderNotFoundException(LlmProvider provider) {
            super("LLM provider not found");
            this.provider = provider;
        }

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.NOT_FOUND;
        }

        @Override
        public String errorCode() {
            return "error.admin.llm_provider_not_found";
        }

        @Override
        public String logEvent() {
            return "admin_llm_provider_not_found";
        }

        @Override
        public String detail() {
            return "The provider does not exist in the LLM provider catalog.";
        }

        @Override
        public Map<String, Object> params() {
            return Map.of("provider", provider.id());
        }
    }

    public static class ProviderDeleteNotAllowedException extends AdminBusinessException {

        private final LlmProvider provider;

        public ProviderDeleteNotAllowedException(LlmProvider provider) {
            super("LLM provider cannot be deleted");
            this.provider = provider;
        }

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.BAD_REQUEST;
        }

        @Override
        public String errorCode() {
            return "error.admin.llm_provider_delete_not_allowed";
        }

        @Override
        public String logEvent() {
            return "admin_llm_provider_delete_not_allowed";
        }

        @Override
        public String detail() {
            return "Only compatible gateway providers can be deleted.";
        }

        @Override
        public Map<String, Object> params() {
            return Map.of("provider", provider.id());
        }
    }

    public static class ProviderDeleteBlockedException extends AdminBusinessException {

        private final LlmProvider provider;
        private final long routingReferenceCount;
        private final long pinnedTenantCount;

        public ProviderDeleteBlockedException(
                LlmProvider provider, long routingReferenceCount, long pinnedTenantCount) {
            super("LLM provider is still in use");
            this.provider = provider;
            this.routingReferenceCount = routingReferenceCount;
            this.pinnedTenantCount = pinnedTenantCount;
        }

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.CONFLICT;
        }

        @Override
        public String errorCode() {
            return "error.admin.llm_provider_delete_blocked";
        }

        @Override
        public String logEvent() {
            return "admin_llm_provider_delete_blocked";
        }

        @Override
        public String detail() {
            return "The provider is still referenced by routing or tenant model settings.";
        }

        @Override
        public Map<String, Object> params() {
            return Map.of(
                    "provider",
                    provider.id(),
                    "routingReferenceCount",
                    routingReferenceCount,
                    "pinnedTenantCount",
                    pinnedTenantCount);
        }
    }

    public static class ProviderBaseUrlInvalidException extends AdminBusinessException {

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.BAD_REQUEST;
        }

        @Override
        public String errorCode() {
            return "error.admin.llm_provider_base_url_invalid";
        }

        @Override
        public String logEvent() {
            return "admin_llm_provider_base_url_invalid";
        }

        @Override
        public String detail() {
            return "The provider base URL must be an http or https URL.";
        }
    }

    public static class ProviderDisplayNameInvalidException extends AdminBusinessException {

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.BAD_REQUEST;
        }

        @Override
        public String errorCode() {
            return "error.admin.llm_provider_display_name_invalid";
        }

        @Override
        public String logEvent() {
            return "admin_llm_provider_display_name_invalid";
        }

        @Override
        public String detail() {
            return "The provider display name is required.";
        }
    }
}
