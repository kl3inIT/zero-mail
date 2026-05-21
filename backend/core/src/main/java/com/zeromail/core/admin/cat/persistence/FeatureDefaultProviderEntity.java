package com.zeromail.core.admin.cat.persistence;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.persistence.LlmProviderAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feature_default_provider")
public class FeatureDefaultProviderEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "feature", nullable = false, length = 16)
    private Feature feature;

    @Convert(converter = LlmProviderAttributeConverter.class)
    @Column(name = "provider", nullable = false, length = 32)
    private LlmProvider provider;

    @Column(name = "model_id", nullable = false, length = 128)
    private String modelId;

    @Column(name = "updated_by_admin")
    private UUID updatedByAdmin;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FeatureDefaultProviderEntity() {
        // Hibernate
    }

    public Feature getFeature() {
        return feature;
    }

    public LlmProvider getProvider() {
        return provider;
    }

    public String getModelId() {
        return modelId;
    }

    public UUID getUpdatedByAdmin() {
        return updatedByAdmin;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
