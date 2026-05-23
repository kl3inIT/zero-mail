package com.zeromail.core.account.persistence;

import com.zeromail.core.account.domain.OnboardingStep;
import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity extends AbstractTenantOwnedEntity {

    @Column(name = "google_subject", nullable = false, unique = true)
    private String googleSubject;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_step", nullable = false)
    private OnboardingStep onboardingStep = OnboardingStep.GMAIL_CONNECTED;

    @Column(name = "preferred_language", length = 2, nullable = false)
    private String preferredLanguage = "vi";

    protected UserEntity() {}

    public UserEntity(UUID id, UUID tenantId, String googleSubject, String email) {
        super(id, tenantId);
        this.googleSubject = googleSubject;
        this.email = email;
    }

    public String getGoogleSubject() {
        return googleSubject;
    }

    public String getEmail() {
        return email;
    }

    public OnboardingStep getOnboardingStep() {
        return onboardingStep;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    public void advanceTo(OnboardingStep next) {
        if (next.weight() < this.onboardingStep.weight()) {
            throw new IllegalStateException("Onboarding state is forward-only");
        }
        this.onboardingStep = next;
    }
}
