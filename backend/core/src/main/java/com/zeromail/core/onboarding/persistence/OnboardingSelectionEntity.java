package com.zeromail.core.onboarding.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "onboarding_selections")
public class OnboardingSelectionEntity {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "template_key", nullable = false)
    private String templateKey;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected OnboardingSelectionEntity() {}

    public OnboardingSelectionEntity(UUID id, UUID tenantId, String templateKey) {
        this.id = id;
        this.tenantId = tenantId;
        this.templateKey = templateKey;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getTemplateKey() { return templateKey; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
