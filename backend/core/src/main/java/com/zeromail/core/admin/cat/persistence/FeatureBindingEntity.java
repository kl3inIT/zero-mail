package com.zeromail.core.admin.cat.persistence;

import com.zeromail.core.admin.cat.domain.Feature;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feature_binding")
public class FeatureBindingEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "model_id", nullable = false, length = 128)
    private String modelId;

    @Convert(converter = FeatureAttributeConverter.class)
    @Column(name = "feature", nullable = false, length = 16)
    private Feature feature;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FeatureBindingEntity() {
        // Hibernate
    }

    public UUID getId() {
        return id;
    }

    public String getModelId() {
        return modelId;
    }

    public Feature getFeature() {
        return feature;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
