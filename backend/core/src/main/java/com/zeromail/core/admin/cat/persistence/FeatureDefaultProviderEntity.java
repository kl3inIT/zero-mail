package com.zeromail.core.admin.cat.persistence;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.RoutingTier;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.persistence.LlmProviderAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One row per (feature, tier). Together the three tier rows for a feature form its PRIMARY →
 * FALLBACK → LAST_RESORT routing chain.
 */
@Entity
@Table(name = "feature_default_provider")
@IdClass(FeatureDefaultProviderId.class)
public class FeatureDefaultProviderEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "feature", nullable = false, length = 16)
    private Feature feature;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 16)
    private RoutingTier tier;

    @Convert(converter = LlmProviderAttributeConverter.class)
    @Column(name = "provider", nullable = false, length = 32)
    private LlmProvider provider;

    @Column(name = "updated_by_admin")
    private UUID updatedByAdmin;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FeatureDefaultProviderEntity() {
        // Hibernate
    }

    public FeatureDefaultProviderEntity(
            Feature feature,
            RoutingTier tier,
            LlmProvider provider,
            UUID updatedByAdmin,
            Instant updatedAt) {
        this.feature = feature;
        this.tier = tier;
        this.provider = provider;
        this.updatedByAdmin = updatedByAdmin;
        this.updatedAt = updatedAt;
    }

    public Feature getFeature() {
        return feature;
    }

    public RoutingTier getTier() {
        return tier;
    }

    public LlmProvider getProvider() {
        return provider;
    }

    public UUID getUpdatedByAdmin() {
        return updatedByAdmin;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public FeatureDefaultProviderId getId() {
        return new FeatureDefaultProviderId(feature, tier);
    }

    public void assignTo(LlmProvider provider, UUID updatedByAdmin, Instant now) {
        this.provider = provider;
        this.updatedByAdmin = updatedByAdmin;
        this.updatedAt = now;
    }
}
