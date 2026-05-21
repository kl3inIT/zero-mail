package com.zeromail.core.chat.persistence;

import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

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
}
