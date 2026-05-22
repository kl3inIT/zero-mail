package com.zeromail.core.admin.cat.persistence;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.RoutingTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Ordered model slot inside a (feature, tier). Position 1 is tried first; the router only advances
 * to position 2 after the model at position 1 fails. A tier's slots share the provider declared on
 * {@link FeatureDefaultProviderEntity}.
 */
@Entity
@Table(name = "feature_tier_model")
@IdClass(FeatureTierModelId.class)
public class FeatureTierModelEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "feature", nullable = false, length = 16)
    private Feature feature;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 16)
    private RoutingTier tier;

    @Id
    @Column(name = "position", nullable = false)
    private short position;

    @Column(name = "model_id", nullable = false, length = 128)
    private String modelId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FeatureTierModelEntity() {
        // Hibernate
    }

    public FeatureTierModelEntity(
            Feature feature, RoutingTier tier, short position, String modelId, Instant updatedAt) {
        this.feature = feature;
        this.tier = tier;
        this.position = position;
        this.modelId = modelId;
        this.updatedAt = updatedAt;
    }

    public Feature getFeature() {
        return feature;
    }

    public RoutingTier getTier() {
        return tier;
    }

    public short getPosition() {
        return position;
    }

    public String getModelId() {
        return modelId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public FeatureTierModelId getId() {
        return new FeatureTierModelId(feature, tier, position);
    }
}
