package com.zeromail.core.llm.gateway.springai.admin;

import com.zeromail.core.admin.mkey.domain.KeyFormat;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.persistence.LlmProviderMasterKeyEntity;
import com.zeromail.core.admin.mkey.persistence.LlmProviderMasterKeyRepository;
import com.zeromail.core.admin.mkey.projection.MasterKeyMaskedRow;
import com.zeromail.core.admin.mkey.usecases.MasterKeyMasker;
import com.zeromail.core.shared.crypto.PlatformSecretCipher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProviderMasterKeyResolver {

    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(15);
    private static final Duration ROTATION_RECOMMENDED_AFTER = Duration.ofDays(90);

    private final LlmProviderMasterKeyRepository llmProviderMasterKeyRepository;
    private final PlatformSecretCipher platformSecretCipher;
    private final Clock clock;
    private final Duration cacheTtl;
    private final ConcurrentMap<LlmProvider, CachedMasterKey> cachedKeysByProvider =
            new ConcurrentHashMap<>();

    @Autowired
    public ProviderMasterKeyResolver(
            LlmProviderMasterKeyRepository llmProviderMasterKeyRepository,
            PlatformSecretCipher platformSecretCipher) {
        this(
                llmProviderMasterKeyRepository,
                platformSecretCipher,
                Clock.systemUTC(),
                DEFAULT_CACHE_TTL);
    }

    public ProviderMasterKeyResolver(
            LlmProviderMasterKeyRepository llmProviderMasterKeyRepository,
            PlatformSecretCipher platformSecretCipher,
            Clock clock,
            Duration cacheTtl) {
        this.llmProviderMasterKeyRepository =
                Objects.requireNonNull(
                        llmProviderMasterKeyRepository, "llmProviderMasterKeyRepository");
        this.platformSecretCipher =
                Objects.requireNonNull(platformSecretCipher, "platformSecretCipher");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.cacheTtl = Objects.requireNonNull(cacheTtl, "cacheTtl must not be null");
    }

    public ResolvedMasterKey resolve(LlmProvider provider) {
        CachedMasterKey cachedMasterKey =
                cachedKeysByProvider.compute(
                        provider,
                        (ignoredProvider, existingCachedKey) -> {
                            if (existingCachedKey != null
                                    && !existingCachedKey.isExpired(clock.instant())) {
                                return existingCachedKey;
                            }
                            if (existingCachedKey != null) {
                                existingCachedKey.wipe();
                            }
                            return load(provider);
                        });
        return cachedMasterKey.toResolved();
    }

    public Optional<ResolvedMasterKey> resolveOptional(LlmProvider provider) {
        try {
            return Optional.of(resolve(provider));
        } catch (MissingMasterKeyException missingMasterKeyException) {
            return Optional.empty();
        }
    }

    public long providerSecretVersionOrZero(LlmProvider provider) {
        return llmProviderMasterKeyRepository
                .findById(provider)
                .map(LlmProviderMasterKeyEntity::getProviderSecretVersion)
                .orElse(0L);
    }

    public List<MasterKeyMaskedRow> maskedRows() {
        return llmProviderMasterKeyRepository.findAllOrderedByProvider().stream()
                .map(this::toMaskedRow)
                .toList();
    }

    public void invalidate(LlmProvider provider) {
        CachedMasterKey cachedMasterKey = cachedKeysByProvider.remove(provider);
        if (cachedMasterKey != null) {
            cachedMasterKey.wipe();
        }
    }

    private CachedMasterKey load(LlmProvider provider) {
        LlmProviderMasterKeyEntity entity =
                llmProviderMasterKeyRepository
                        .findById(provider)
                        .orElseThrow(() -> new MissingMasterKeyException(provider));
        if (!entity.hasEncryptedKey()
                || entity.getKekVersion() == null
                || entity.getKeyFormat() == null) {
            throw new MissingMasterKeyException(provider);
        }
        byte[] plaintextKey =
                platformSecretCipher.decrypt(entity.getEncryptedKey(), associatedData(provider));
        return new CachedMasterKey(
                provider,
                plaintextKey,
                entity.getKeyFormat(),
                resolvedBaseUrl(entity),
                entity.getKekVersion(),
                entity.getProviderSecretVersion(),
                clock.instant(),
                cacheTtl);
    }

    private MasterKeyMaskedRow toMaskedRow(LlmProviderMasterKeyEntity entity) {
        String maskedKey =
                entity.hasEncryptedKey()
                        ? resolveOptional(entity.getProvider())
                                .map(
                                        resolvedMasterKey ->
                                                MasterKeyMasker.mask(
                                                        resolvedMasterKey.plaintextKey(),
                                                        entity.getProvider()))
                                .orElse(null)
                        : null;
        return new MasterKeyMaskedRow(
                entity.getProvider(),
                maskedKey,
                entity.getKeyFormat(),
                entity.getKekVersion(),
                entity.getProviderSecretVersion(),
                entity.getLastRotatedAt(),
                0L,
                rotationRecommended(entity.getLastRotatedAt()),
                resolvedBaseUrl(entity),
                entity.isFeatureDefaultProviderChat(),
                entity.isFeatureDefaultProviderTriage(),
                entity.isFeatureDefaultProviderDraft());
    }

    private boolean rotationRecommended(Instant lastRotatedAt) {
        return lastRotatedAt != null
                && lastRotatedAt.plus(ROTATION_RECOMMENDED_AFTER).isBefore(clock.instant());
    }

    public static String associatedData(LlmProvider provider) {
        return "platform:master_key:" + provider.id();
    }

    private static String resolvedBaseUrl(LlmProviderMasterKeyEntity entity) {
        String baseUrl = entity.getBaseUrl();
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl;
        }
        return entity.getProvider().defaultBaseUrl();
    }

    public record ResolvedMasterKey(
            LlmProvider provider,
            byte[] plaintextKey,
            KeyFormat keyFormat,
            String baseUrl,
            Short kekVersion,
            long providerSecretVersion) {

        public ResolvedMasterKey {
            plaintextKey = Arrays.copyOf(plaintextKey, plaintextKey.length);
        }
    }

    public static class MissingMasterKeyException extends RuntimeException {

        public MissingMasterKeyException(LlmProvider provider) {
            super("No master key configured for provider " + provider.id());
        }
    }

    private static final class CachedMasterKey {

        private final LlmProvider provider;
        private final byte[] plaintextKey;
        private final KeyFormat keyFormat;
        private final String baseUrl;
        private final Short kekVersion;
        private final long providerSecretVersion;
        private final Instant fetchedAt;
        private final Duration cacheTtl;

        private CachedMasterKey(
                LlmProvider provider,
                byte[] plaintextKey,
                KeyFormat keyFormat,
                String baseUrl,
                Short kekVersion,
                long providerSecretVersion,
                Instant fetchedAt,
                Duration cacheTtl) {
            this.provider = provider;
            this.plaintextKey = Arrays.copyOf(plaintextKey, plaintextKey.length);
            this.keyFormat = keyFormat;
            this.baseUrl = baseUrl;
            this.kekVersion = kekVersion;
            this.providerSecretVersion = providerSecretVersion;
            this.fetchedAt = fetchedAt;
            this.cacheTtl = cacheTtl;
        }

        private boolean isExpired(Instant now) {
            return fetchedAt.plus(cacheTtl).isBefore(now);
        }

        private ResolvedMasterKey toResolved() {
            return new ResolvedMasterKey(
                    provider, plaintextKey, keyFormat, baseUrl, kekVersion, providerSecretVersion);
        }

        private void wipe() {
            Arrays.fill(plaintextKey, (byte) 0);
        }
    }
}
