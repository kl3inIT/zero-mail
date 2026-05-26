package com.zeromail.core.chat.settings;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zeromail.core.chat.exception.SettingsValidationException;
import com.zeromail.core.chat.persistence.AssistantSettingsEntity;
import com.zeromail.core.chat.persistence.AssistantSettingsJpaRepository;
import com.zeromail.core.chat.sanitize.PersonalizationSanitizer;
import com.zeromail.core.chat.usecases.settings.SettingsBehaviorCommand;
import com.zeromail.core.chat.usecases.settings.SettingsBehaviorService;
import com.zeromail.core.chat.usecases.settings.SettingsVoiceCommand;
import com.zeromail.core.chat.usecases.settings.SettingsVoiceService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssistantSettingsTonePresetCheckTest {

    @Test
    void voiceSettingsRejectUnknownTonePreset() {
        SettingsVoiceService settingsVoiceService =
                new SettingsVoiceService(repositoryWithDefaults(), new PersonalizationSanitizer());

        assertThatThrownBy(
                        () ->
                                settingsVoiceService.update(
                                        UUID.randomUUID(),
                                        new SettingsVoiceCommand(
                                                null, null, null, "YELLING", null)))
                .isInstanceOf(SettingsValidationException.class)
                .extracting(exception -> ((SettingsValidationException) exception).errorCode())
                .isEqualTo("voice.tone_preset.invalid");
    }

    @Test
    void behaviorSettingsRejectUnknownDraftConfidence() {
        SettingsBehaviorService settingsBehaviorService =
                new SettingsBehaviorService(repositoryWithDefaults());

        assertThatThrownBy(
                        () ->
                                settingsBehaviorService.update(
                                        UUID.randomUUID(),
                                        new SettingsBehaviorCommand(null, "EXTREME", null)))
                .isInstanceOf(SettingsValidationException.class)
                .extracting(exception -> ((SettingsValidationException) exception).errorCode())
                .isEqualTo("behavior.draft_confidence.invalid");
    }

    private static AssistantSettingsJpaRepository repositoryWithDefaults() {
        AssistantSettingsJpaRepository assistantSettingsRepository =
                mock(AssistantSettingsJpaRepository.class);
        when(assistantSettingsRepository.findByTenantId(any()))
                .thenReturn(Optional.of(AssistantSettingsEntity.defaults(UUID.randomUUID())));
        when(assistantSettingsRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return assistantSettingsRepository;
    }
}
