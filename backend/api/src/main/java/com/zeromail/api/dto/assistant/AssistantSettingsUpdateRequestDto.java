package com.zeromail.api.dto.assistant;

import com.zeromail.core.chat.usecases.AssistantSettingsService;
import com.zeromail.core.chat.usecases.AssistantSettingsService.AssistantWritingProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /api/assistant/settings}.
 *
 * <p>Send {@code null} or empty string to clear a field. All fields are independently optional —
 * the upsert replaces all three atomically.
 *
 * <p>{@code aiOutputLanguage} accepts {@code vi}, {@code en}, or omitted (= let the assistant infer
 * from thread context). Anything outside that closed set is currently treated as opaque text by the
 * renderer and clipped to 8 chars by the column definition.
 */
public record AssistantSettingsUpdateRequestDto(
        @Size(max = AssistantSettingsService.MAX_PERSONAL_INSTRUCTIONS_LENGTH) String personalInstructions,
        @Size(max = AssistantSettingsService.MAX_WRITING_STYLE_LENGTH) String writingStyle,
        @Schema(allowableValues = {"vi", "en"})
                @Size(max = AssistantSettingsService.MAX_OUTPUT_LANGUAGE_LENGTH) String aiOutputLanguage) {

    public AssistantWritingProfile toProfile() {
        return new AssistantWritingProfile(personalInstructions, writingStyle, aiOutputLanguage);
    }
}
