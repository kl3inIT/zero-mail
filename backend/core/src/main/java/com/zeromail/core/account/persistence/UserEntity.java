package com.zeromail.core.account.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.TenantId;

import com.zeromail.core.onboarding.model.OnboardingStep;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "google_subject", nullable = false, unique = true)
    private String googleSubject;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_step", nullable = false)
    private OnboardingStep onboardingStep = OnboardingStep.SIGNED_IN;

    @Column(name = "preferred_language", length = 2, nullable = false)
    private String preferredLanguage = "vi";

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected UserEntity() {}

    public UserEntity(UUID id, UUID tenantId, String googleSubject, String email) {
        this.id = id;
        this.tenantId = tenantId;
        this.googleSubject = googleSubject;
        this.email = email;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getGoogleSubject() { return googleSubject; }
    public String getEmail() { return email; }
    public OnboardingStep getOnboardingStep() { return onboardingStep; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
    public Instant getCreatedAt() { return createdAt; }

    public void advanceTo(OnboardingStep next) {
        if (next.ordinal() < this.onboardingStep.ordinal()) {
            throw new IllegalStateException("Onboarding state is forward-only");
        }
        this.onboardingStep = next;
    }
}
