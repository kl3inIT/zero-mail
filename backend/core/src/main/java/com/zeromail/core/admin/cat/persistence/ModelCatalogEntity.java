package com.zeromail.core.admin.cat.persistence;

import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.persistence.LlmProviderAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "model_catalog")
public class ModelCatalogEntity {

    @Id
    @Column(name = "model_id", nullable = false, length = 128)
    private String modelId;

    @Convert(converter = LlmProviderAttributeConverter.class)
    @Column(name = "provider", nullable = false, length = 32)
    private LlmProvider provider;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "cost_per_1k_input", precision = 10, scale = 6)
    private BigDecimal costPer1kInput;

    @Column(name = "cost_per_1k_output", precision = 10, scale = 6)
    private BigDecimal costPer1kOutput;

    @Column(name = "deprecated_at")
    private Instant deprecatedAt;

    @Column(name = "is_recommended", nullable = false)
    private boolean recommended;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ModelCatalogEntity() {
        // Hibernate
    }

    public String getModelId() {
        return modelId;
    }

    public LlmProvider getProvider() {
        return provider;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BigDecimal getCostPer1kInput() {
        return costPer1kInput;
    }

    public BigDecimal getCostPer1kOutput() {
        return costPer1kOutput;
    }

    public Instant getDeprecatedAt() {
        return deprecatedAt;
    }

    public boolean isRecommended() {
        return recommended;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
