package com.zeromail.core.llm.byok;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Stream;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_byok_key")
@SuppressWarnings({"JpaDataSourceORMInspection", "unused"})
public class UserByokKeyEntity {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provider", nullable = false, length = 16)
    private String provider;

    @Column(name = "base_url", nullable = false, length = 255)
    private String baseUrl;

    @Column(name = "api_key_ciphertext", nullable = false)
    private byte[] apiKeyCiphertext;

    @Column(name = "model_id", length = 64)
    private String modelId;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "last_test_result", length = 16)
    private String lastTestResult;

    @Column(name = "last_tested_at")
    private Instant lastTestedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_test_models_json", columnDefinition = "jsonb")
    private String lastTestModelsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserByokKeyEntity() {
        // Hibernate
    }

    public UserByokKeyEntity(
            UUID tenantId,
            Provider provider,
            String baseUrl,
            byte[] apiKeyCiphertext,
            String modelId) {
        this.tenantId = requireTenantId(tenantId);
        this.provider = requireProvider(provider).id();
        this.baseUrl = requireText(baseUrl, "baseUrl");
        this.apiKeyCiphertext = copyApiKeyCiphertext(apiKeyCiphertext);
        this.modelId = modelId;
        this.active = false;
        this.lastTestResult = null;
        this.lastTestedAt = null;
        this.lastTestModelsJson = null;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getProviderId() {
        return provider;
    }

    public Provider getProvider() {
        return Provider.fromId(provider);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public byte[] getApiKeyCiphertext() {
        return copyApiKeyCiphertext(apiKeyCiphertext);
    }

    public String getModelId() {
        return modelId;
    }

    public boolean isActive() {
        return active;
    }

    public String getLastTestResultId() {
        return lastTestResult;
    }

    public LastTestResult getLastTestResult() {
        return lastTestResult == null ? null : LastTestResult.fromId(lastTestResult);
    }

    public Instant getLastTestedAt() {
        return lastTestedAt;
    }

    public String getLastTestModelsJson() {
        return lastTestModelsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void replaceCredential(
            Provider provider, String baseUrl, byte[] apiKeyCiphertext, String modelId) {
        this.provider = requireProvider(provider).id();
        this.baseUrl = requireText(baseUrl, "baseUrl");
        this.apiKeyCiphertext = copyApiKeyCiphertext(apiKeyCiphertext);
        this.modelId = modelId;
        this.active = false;
        this.lastTestResult = null;
        this.lastTestedAt = null;
        this.lastTestModelsJson = null;
    }

    public void selectModel(String modelId) {
        this.modelId = modelId;
        this.active = false;
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    public void recordConnectionTest(
            LastTestResult lastTestResult, Instant testedAt, String lastTestModelsJson) {
        this.lastTestResult = requireLastTestResult(lastTestResult).id();
        this.lastTestedAt = testedAt == null ? Instant.now() : testedAt;
        this.lastTestModelsJson = lastTestModelsJson;
    }

    @PrePersist
    private void beforeInsert() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        validateBeforeWrite();
    }

    @PreUpdate
    private void beforeUpdate() {
        updatedAt = Instant.now();
        validateBeforeWrite();
    }

    private void validateBeforeWrite() {
        Provider.fromId(provider);
        if (lastTestResult != null) {
            LastTestResult.fromId(lastTestResult);
        }
        requireText(baseUrl, "baseUrl");
        copyApiKeyCiphertext(apiKeyCiphertext);
    }

    private static UUID requireTenantId(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        return tenantId;
    }

    private static Provider requireProvider(Provider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        return provider;
    }

    private static LastTestResult requireLastTestResult(LastTestResult lastTestResult) {
        if (lastTestResult == null) {
            throw new IllegalArgumentException("lastTestResult must not be null");
        }
        return lastTestResult;
    }

    private static String requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
    }

    private static byte[] copyApiKeyCiphertext(byte[] apiKeyCiphertext) {
        if (apiKeyCiphertext == null || apiKeyCiphertext.length == 0) {
            throw new IllegalArgumentException("apiKeyCiphertext must not be empty");
        }
        return Arrays.copyOf(apiKeyCiphertext, apiKeyCiphertext.length);
    }

    public enum Provider {
        OPENAI,
        ANTHROPIC,
        GOOGLE,
        DEEPSEEK;

        public String id() {
            return name();
        }

        public static Provider fromId(String id) {
            return Stream.of(values())
                    .filter(provider -> provider.id().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchElementException("Unknown Provider id: " + id));
        }
    }

    public enum LastTestResult {
        OK,
        INVALID_KEY,
        RATE_LIMITED,
        NETWORK_ERROR,
        TIMEOUT;

        public String id() {
            return name();
        }

        public static LastTestResult fromId(String id) {
            return Stream.of(values())
                    .filter(lastTestResult -> lastTestResult.id().equals(id))
                    .findFirst()
                    .orElseThrow(
                            () -> new NoSuchElementException("Unknown LastTestResult id: " + id));
        }
    }
}
