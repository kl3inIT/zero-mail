package com.zeromail.core.chat.usecases.settings;

import com.zeromail.core.chat.exception.SettingsValidationException;
import com.zeromail.core.chat.persistence.AssistantSettingsEntity;
import com.zeromail.core.chat.persistence.AssistantSettingsJpaRepository;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsBehaviorService {

    private final AssistantSettingsJpaRepository assistantSettingsRepository;

    public SettingsBehaviorService(AssistantSettingsJpaRepository assistantSettingsRepository) {
        this.assistantSettingsRepository = assistantSettingsRepository;
    }

    @Transactional(readOnly = true)
    public SettingsBehaviorResult get(UUID tenantId) {
        return toResult(loadOrDefault(tenantId));
    }

    @Transactional
    public SettingsBehaviorResult update(UUID tenantId, SettingsBehaviorCommand command) {
        AssistantSettingsEntity assistantSettings = loadOrDefault(tenantId);
        apply(assistantSettings, command);
        try {
            return toResult(assistantSettingsRepository.saveAndFlush(assistantSettings));
        } catch (DataIntegrityViolationException concurrentInsertFailure) {
            AssistantSettingsEntity existingAssistantSettings =
                    assistantSettingsRepository
                            .findByTenantId(tenantId)
                            .orElseThrow(() -> concurrentInsertFailure);
            apply(existingAssistantSettings, command);
            return toResult(assistantSettingsRepository.saveAndFlush(existingAssistantSettings));
        }
    }

    private AssistantSettingsEntity loadOrDefault(UUID tenantId) {
        return assistantSettingsRepository
                .findByTenantId(tenantId)
                .orElseGet(() -> AssistantSettingsEntity.defaults(tenantId));
    }

    private void apply(AssistantSettingsEntity assistantSettings, SettingsBehaviorCommand command) {
        boolean autoDraftReplies =
                command.autoDraftReplies() == null
                        ? assistantSettings.isAutoDraftReplies()
                        : command.autoDraftReplies();
        AssistantSettingsEntity.DraftConfidence draftConfidence =
                command.draftConfidence() == null
                        ? assistantSettings.getDraftConfidence()
                        : parseDraftConfidence(command.draftConfidence());
        boolean sensitiveDataProtection =
                command.sensitiveDataProtection() == null
                        ? assistantSettings.isSensitiveDataProtection()
                        : command.sensitiveDataProtection();
        assistantSettings.applyBehaviorSettings(
                autoDraftReplies, draftConfidence, sensitiveDataProtection);
    }

    private static AssistantSettingsEntity.DraftConfidence parseDraftConfidence(
            String draftConfidence) {
        try {
            return AssistantSettingsEntity.DraftConfidence.fromId(draftConfidence);
        } catch (NoSuchElementException unknownDraftConfidence) {
            throw SettingsValidationException.invalidDraftConfidence();
        }
    }

    private static SettingsBehaviorResult toResult(AssistantSettingsEntity assistantSettings) {
        return new SettingsBehaviorResult(
                assistantSettings.isAutoDraftReplies(),
                assistantSettings.getDraftConfidenceId(),
                assistantSettings.isSensitiveDataProtection());
    }
}
