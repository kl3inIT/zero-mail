package com.zeromail.core.chat.usecases.settings;

import com.zeromail.core.chat.exception.SettingsValidationException;
import com.zeromail.core.chat.persistence.AssistantSettingsEntity;
import com.zeromail.core.chat.persistence.AssistantSettingsJpaRepository;
import com.zeromail.core.chat.sanitize.PersonalizationSanitizer;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsVoiceService {

    private static final int MIN_WRITING_STYLE_WORDS = 200;
    private static final int MAX_WRITING_STYLE_WORDS = 500;
    private static final int MAX_PERSONAL_INSTRUCTIONS_LENGTH = 2_000;

    private final AssistantSettingsJpaRepository assistantSettingsRepository;
    private final PersonalizationSanitizer personalizationSanitizer;

    public SettingsVoiceService(
            AssistantSettingsJpaRepository assistantSettingsRepository,
            PersonalizationSanitizer personalizationSanitizer) {
        this.assistantSettingsRepository = assistantSettingsRepository;
        this.personalizationSanitizer = personalizationSanitizer;
    }

    @Transactional(readOnly = true)
    public SettingsVoiceResult get(UUID tenantId) {
        return toResult(loadOrDefault(tenantId));
    }

    @Transactional
    public SettingsVoiceResult update(UUID tenantId, SettingsVoiceCommand command) {
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

    private void apply(AssistantSettingsEntity assistantSettings, SettingsVoiceCommand command) {
        String writingStyle =
                command.writingStyle() == null
                        ? assistantSettings.getWritingStyle()
                        : validateWritingStyle(command.writingStyle());
        String personalInstructions =
                command.personalInstructions() == null
                        ? assistantSettings.getPersonalInstructions()
                        : sanitizePersonalInstructions(command.personalInstructions());
        String emailSignature =
                command.emailSignature() == null
                        ? assistantSettings.getEmailSignature()
                        : command.emailSignature();
        AssistantSettingsEntity.TonePreset tonePreset =
                command.tonePreset() == null
                        ? assistantSettings.getTonePreset()
                        : parseTonePreset(command.tonePreset());
        String aiOutputLanguage =
                command.aiOutputLanguage() == null
                        ? assistantSettings.getAiOutputLanguage()
                        : requireSupportedLanguage(command.aiOutputLanguage());

        assistantSettings.applyVoiceSettings(
                writingStyle, personalInstructions, emailSignature, tonePreset, aiOutputLanguage);
    }

    private static String validateWritingStyle(String writingStyle) {
        int wordCount = countWords(writingStyle);
        if (wordCount < MIN_WRITING_STYLE_WORDS) {
            throw SettingsValidationException.writingStyleTooShort();
        }
        if (wordCount > MAX_WRITING_STYLE_WORDS) {
            throw SettingsValidationException.writingStyleTooLong();
        }
        return writingStyle;
    }

    private String sanitizePersonalInstructions(String personalInstructions) {
        String sanitizedPersonalInstructions =
                personalizationSanitizer.sanitize(personalInstructions);
        if (sanitizedPersonalInstructions.length() > MAX_PERSONAL_INSTRUCTIONS_LENGTH) {
            throw SettingsValidationException.personalInstructionsTooLong();
        }
        return sanitizedPersonalInstructions;
    }

    private static AssistantSettingsEntity.TonePreset parseTonePreset(String tonePreset) {
        try {
            return AssistantSettingsEntity.TonePreset.fromId(tonePreset);
        } catch (NoSuchElementException unknownTonePreset) {
            throw SettingsValidationException.invalidTonePreset();
        }
    }

    private static String requireSupportedLanguage(String aiOutputLanguage) {
        if (!"vi".equals(aiOutputLanguage) && !"en".equals(aiOutputLanguage)) {
            throw new IllegalArgumentException("aiOutputLanguage must be vi or en");
        }
        return aiOutputLanguage;
    }

    private static int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private static SettingsVoiceResult toResult(AssistantSettingsEntity assistantSettings) {
        return new SettingsVoiceResult(
                assistantSettings.getWritingStyle(),
                assistantSettings.getPersonalInstructions(),
                assistantSettings.getEmailSignature(),
                assistantSettings.getTonePresetId(),
                assistantSettings.getAiOutputLanguage());
    }
}
