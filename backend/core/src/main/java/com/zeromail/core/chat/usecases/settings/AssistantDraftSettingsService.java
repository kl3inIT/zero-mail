package com.zeromail.core.chat.usecases.settings;

import com.zeromail.core.chat.persistence.AssistantSettingsEntity;
import com.zeromail.core.chat.persistence.AssistantSettingsJpaRepository;
import com.zeromail.core.llm.usecases.SensitiveDataProtectionDecider;
import com.zeromail.core.triage.usecases.TriageDraftSettings;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssistantDraftSettingsService
        implements SensitiveDataProtectionDecider, TriageDraftSettings {

    private final AssistantSettingsJpaRepository assistantSettingsRepository;

    public AssistantDraftSettingsService(
            AssistantSettingsJpaRepository assistantSettingsRepository) {
        this.assistantSettingsRepository =
                Objects.requireNonNull(
                        assistantSettingsRepository,
                        "assistantSettingsRepository must not be null");
    }

    @Transactional(readOnly = true)
    public boolean autoDraftRepliesEnabled(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return assistantSettingsRepository
                .findByTenantId(tenantId)
                .map(AssistantSettingsEntity::isAutoDraftReplies)
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public Optional<String> emailSignature(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return assistantSettingsRepository
                .findByTenantId(tenantId)
                .map(AssistantSettingsEntity::getEmailSignature)
                .filter(emailSignature -> !emailSignature.isBlank());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSensitiveDataProtectionEnabled(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return assistantSettingsRepository
                .findByTenantId(tenantId)
                .map(AssistantSettingsEntity::isSensitiveDataProtection)
                .orElse(true);
    }
}
