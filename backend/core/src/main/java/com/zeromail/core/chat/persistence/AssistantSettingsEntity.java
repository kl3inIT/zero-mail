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
@SuppressWarnings("JpaDataSourceORMInspection")
public class AssistantSettingsEntity extends AbstractTenantOwnedEntity {

    @Column(name = "personal_instructions")
    private String personalInstructions;

    @Column(name = "writing_style")
    private String writingStyle;

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
}
