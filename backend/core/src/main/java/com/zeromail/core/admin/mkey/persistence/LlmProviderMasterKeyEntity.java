package com.zeromail.core.admin.mkey.persistence;

import com.zeromail.core.admin.mkey.domain.KeyFormat;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@Entity
@Table(name = "llm_provider_master_key")
public class LlmProviderMasterKeyEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private LlmProvider provider;

    @Convert(converter = KeyFormatAttributeConverter.class)
    @Column(name = "key_format", length = 32)
    private KeyFormat keyFormat;

    @Column(name = "encrypted_key")
    private byte[] encryptedKey;

    @Column(name = "kek_version")
    private Short kekVersion;

    @Column(name = "provider_secret_version", nullable = false)
    private long providerSecretVersion;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_rotated_at")
    private Instant lastRotatedAt;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    protected LlmProviderMasterKeyEntity() {
        // Hibernate
    }

    public LlmProviderMasterKeyEntity(
            LlmProvider provider,
            KeyFormat keyFormat,
            byte[] encryptedKey,
            Short kekVersion,
            long providerSecretVersion,
            UUID createdByUserId,
            Instant createdAt,
            Instant lastRotatedAt,
            String baseUrl) {
        this.provider = provider;
        this.keyFormat = keyFormat;
        this.encryptedKey = copyEncryptedKey(encryptedKey);
        this.kekVersion = kekVersion;
        this.providerSecretVersion = providerSecretVersion;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
        this.lastRotatedAt = lastRotatedAt;
        this.baseUrl = baseUrl;
    }

    public LlmProvider getProvider() {
        return provider;
    }

    public KeyFormat getKeyFormat() {
        return keyFormat;
    }

    public byte[] getEncryptedKey() {
        return copyEncryptedKey(encryptedKey);
    }

    public Short getKekVersion() {
        return kekVersion;
    }

    public long getProviderSecretVersion() {
        return providerSecretVersion;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastRotatedAt() {
        return lastRotatedAt;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean hasEncryptedKey() {
        return encryptedKey != null && encryptedKey.length > 0;
    }

    private static byte[] copyEncryptedKey(byte[] encryptedKey) {
        return encryptedKey == null ? null : Arrays.copyOf(encryptedKey, encryptedKey.length);
    }
}
