package com.zeromail.core.chat.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zeromail.core.chat.exception.SettingsValidationException;
import com.zeromail.core.chat.persistence.AssistantSettingsEntity;
import com.zeromail.core.chat.persistence.AssistantSettingsJpaRepository;
import com.zeromail.core.chat.sanitize.PersonalizationSanitizer;
import com.zeromail.core.chat.usecases.settings.SettingsVoiceCommand;
import com.zeromail.core.chat.usecases.settings.SettingsVoiceService;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SettingsVoiceServiceWordBoundsTest {

    @ParameterizedTest
    @ValueSource(ints = {199, 501})
    void updateRejectsWritingStyleOutsideWordBounds(int wordCount) {
        SettingsVoiceService settingsVoiceService = serviceWithDefaults();

        assertThatThrownBy(
                        () ->
                                settingsVoiceService.update(
                                        UUID.randomUUID(),
                                        new SettingsVoiceCommand(
                                                words(wordCount), null, null, null, null)))
                .isInstanceOf(SettingsValidationException.class)
                .extracting(exception -> ((SettingsValidationException) exception).errorCode())
                .isEqualTo(
                        wordCount < 200
                                ? "voice.writing_style.too_short"
                                : "voice.writing_style.too_long");
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 500})
    void updateAcceptsWritingStyleAtInclusiveWordBounds(int wordCount) {
        SettingsVoiceService settingsVoiceService = serviceWithDefaults();

        assertThat(
                        settingsVoiceService
                                .update(
                                        UUID.randomUUID(),
                                        new SettingsVoiceCommand(
                                                words(wordCount), null, null, null, null))
                                .writingStyle())
                .isEqualTo(words(wordCount));
    }

    private static SettingsVoiceService serviceWithDefaults() {
        AssistantSettingsJpaRepository assistantSettingsRepository =
                mock(AssistantSettingsJpaRepository.class);
        when(assistantSettingsRepository.findByTenantId(any()))
                .thenReturn(Optional.of(AssistantSettingsEntity.defaults(UUID.randomUUID())));
        when(assistantSettingsRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return new SettingsVoiceService(
                assistantSettingsRepository, new PersonalizationSanitizer());
    }

    private static String words(int wordCount) {
        return String.join(" ", IntStream.range(0, wordCount).mapToObj(index -> "word").toList());
    }
}
