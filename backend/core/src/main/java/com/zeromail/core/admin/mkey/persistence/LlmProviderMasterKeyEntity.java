package com.zeromail.core.admin.mkey.persistence;

import com.zeromail.core.admin.mkey.domain.MasterKeyStatus;
import com.zeromail.core.llm.domain.KeyFormat;
import com.zeromail.core.llm.domain.LlmProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/**
 * One row per LLM provider master key. With Phase B v2 the table is keyed by {@code (provider,
 * key_id)} so a provider can hold multiple priority-ordered keys (failover chain).
 */
@Entity
@Table(name = "llm_provider_master_key")
@IdClass(LlmProviderMasterKeyId.class)
public class LlmProviderMasterKeyEntity {

    @Id
    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Id
    @Column(name = "key_id", nullable = false)
    private UUID keyId;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MasterKeyStatus status;

    @Column(name = "label", length = 64)
    private String label;

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

    @Column(name = "masked_key", length = 64)
    private String maskedKey;

    protected LlmProviderMasterKeyEntity() {
        // Hibernate
    }

    public LlmProviderMasterKeyEntity(
            LlmProvider provider,
            UUID keyId,
            int priority,
            MasterKeyStatus status,
            String label,
            KeyFormat keyFormat,
            byte[] encryptedKey,
            Short kekVersion,
            long providerSecretVersion,
            UUID createdByUserId,
            Instant createdAt,
            Instant lastRotatedAt,
            String baseUrl,
            String maskedKey) {
        this.provider = provider.id();
        this.keyId = keyId;
        this.priority = priority;
        this.status = status;
        this.label = label;
        this.keyFormat = keyFormat;
        this.encryptedKey = copyEncryptedKey(encryptedKey);
        this.kekVersion = kekVersion;
        this.providerSecretVersion = providerSecretVersion;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
        this.lastRotatedAt = lastRotatedAt;
        this.baseUrl = baseUrl;
        this.maskedKey = maskedKey;
    }

    public LlmProvider getProvider() {
        return LlmProvider.fromId(provider);
    }

    public String getProviderId() {
        return provider;
    }

    public UUID getKeyId() {
        return keyId;
    }

    public LlmProviderMasterKeyId getId() {
        return new LlmProviderMasterKeyId(provider, keyId);
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public MasterKeyStatus getStatus() {
        return status;
    }

    public void setStatus(MasterKeyStatus status) {
        this.status = status;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
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

    public String getMaskedKey() {
        return maskedKey;
    }

    public boolean hasEncryptedKey() {
        return encryptedKey != null && encryptedKey.length > 0;
    }

    private static byte[] copyEncryptedKey(byte[] encryptedKey) {
        return encryptedKey == null ? null : Arrays.copyOf(encryptedKey, encryptedKey.length);
    }
}
