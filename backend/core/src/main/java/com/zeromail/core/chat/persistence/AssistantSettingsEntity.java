package com.zeromail.core.chat.persistence;

import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Stream;

@Entity
@Table(name = "assistant_settings")
@AttributeOverride(name = "id", column = @Column(name = "assistant_settings_id", nullable = false))
@SuppressWarnings({"JpaDataSourceORMInspection", "unused"})
public class AssistantSettingsEntity extends AbstractTenantOwnedEntity {

    @Column(name = "personal_instructions")
    private String personalInstructions;

    @Column(name = "writing_style")
    private String writingStyle;

    @Column(name = "provider_id", length = 32)
    private String providerId;

    @Column(name = "default_model", length = 64)
    private String defaultModel;

    @Column(name = "chat_model_id", length = 128)
    private String chatModelId;

    @Column(name = "triage_model_id", length = 128)
    private String triageModelId;

    @Column(name = "draft_model_id", length = 128)
    private String draftModelId;

    @Column(name = "ai_output_language", length = 8)
    private String aiOutputLanguage;

    @Column(name = "email_signature", length = 500)
    private String emailSignature;

    @Column(name = "auto_draft_replies", nullable = false)
    private boolean autoDraftReplies = true;

    @Column(name = "draft_confidence", nullable = false, length = 8)
    private String draftConfidence = DraftConfidence.MEDIUM.id();

    @Column(name = "sensitive_data_protection", nullable = false)
    private boolean sensitiveDataProtection = true;

    protected AssistantSettingsEntity() {
        // Hibernate
    }

    public AssistantSettingsEntity(
            UUID assistantSettingsId,
            UUID tenantId,
            String personalInstructions,
            String writingStyle) {
        super(assistantSettingsId, tenantId);
        this.personalInstructions = personalInstructions;
        this.writingStyle = writingStyle;
    }

    public static AssistantSettingsEntity defaults(UUID tenantId) {
        return new AssistantSettingsEntity(UUID.randomUUID(), tenantId, null, null);
    }

    public String getPersonalInstructions() {
        return personalInstructions;
    }

    public String getWritingStyle() {
        return writingStyle;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public String getChatModelId() {
        return chatModelId;
    }

    public String getTriageModelId() {
        return triageModelId;
    }

    public String getDraftModelId() {
        return draftModelId;
    }

    public String getAiOutputLanguage() {
        return aiOutputLanguage;
    }

    public String getEmailSignature() {
        return emailSignature;
    }

    public boolean isAutoDraftReplies() {
        return autoDraftReplies;
    }

    public String getDraftConfidenceId() {
        return draftConfidence;
    }

    public DraftConfidence getDraftConfidence() {
        return DraftConfidence.fromId(draftConfidence);
    }

    public boolean isSensitiveDataProtection() {
        return sensitiveDataProtection;
    }

    public void applyVoiceSettings(
            String writingStyle,
            String personalInstructions,
            String emailSignature,
            String aiOutputLanguage) {
        this.writingStyle = writingStyle;
        this.personalInstructions = personalInstructions;
        this.emailSignature = emailSignature;
        this.aiOutputLanguage = aiOutputLanguage;
    }

    public void applyBehaviorSettings(
            boolean autoDraftReplies,
            DraftConfidence draftConfidence,
            boolean sensitiveDataProtection) {
        this.autoDraftReplies = autoDraftReplies;
        this.draftConfidence = requireDraftConfidence(draftConfidence).id();
        this.sensitiveDataProtection = sensitiveDataProtection;
    }

    private static DraftConfidence requireDraftConfidence(DraftConfidence draftConfidence) {
        if (draftConfidence == null) {
            throw new IllegalArgumentException("draftConfidence must not be null");
        }
        return draftConfidence;
    }

    public enum DraftConfidence {
        LOW,
        MEDIUM,
        HIGH;

        public String id() {
            return name();
        }

        public static DraftConfidence fromId(String id) {
            return Stream.of(values())
                    .filter(draftConfidence -> draftConfidence.id().equals(id))
                    .findFirst()
                    .orElseThrow(
                            () -> new NoSuchElementException("Unknown DraftConfidence id: " + id));
        }
    }
}
