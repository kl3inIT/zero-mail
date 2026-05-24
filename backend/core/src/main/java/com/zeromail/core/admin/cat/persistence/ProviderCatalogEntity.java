package com.zeromail.core.admin.cat.persistence;

import com.zeromail.core.admin.mkey.domain.LlmProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "provider_catalog")
public class ProviderCatalogEntity {

    @Id
    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "provider_kind", nullable = false, length = 32)
    private String providerKind;

    @Column(name = "compatible_type", length = 32)
    private String compatibleType;

    @Column(name = "default_base_url", length = 500)
    private String defaultBaseUrl;

    @Column(name = "catalog_version", nullable = false)
    private long catalogVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    protected ProviderCatalogEntity() {
        // Hibernate
    }

    public LlmProvider getProvider() {
        return LlmProvider.fromId(provider);
    }

    public String getProviderId() {
        return provider;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getProviderKind() {
        return providerKind;
    }

    public String getCompatibleType() {
        return compatibleType;
    }

    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    public long getCatalogVersion() {
        return catalogVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }
}
