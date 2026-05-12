package com.zeromail.core.onboarding.persistence;

import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "onboarding_selections")
public class OnboardingSelectionEntity extends AbstractTenantOwnedEntity {

    @Column(name = "template_key", nullable = false)
    private String templateKey;

    @Column(nullable = false)
    private boolean enabled = true;

    protected OnboardingSelectionEntity() {}

    public OnboardingSelectionEntity(UUID id, UUID tenantId, String templateKey) {
        super(id, tenantId);
        this.templateKey = templateKey;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
