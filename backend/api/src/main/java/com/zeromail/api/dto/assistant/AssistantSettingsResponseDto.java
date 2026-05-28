package com.zeromail.api.dto.assistant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.chat.usecases.AssistantSettingsService.AssistantWritingProfile;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Snapshot of the tenant's assistant writing profile.
 *
 * <p>Returned by {@code GET /api/assistant/settings} and {@code PUT /api/assistant/settings}.
 * Fields are nullable when the tenant has not set them yet — the UI treats null as empty input.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(
        description =
                "Tenant-scoped writing profile injected into the chat assistant and the inbox"
                        + " composer Generate prompt. Fields may be null when unset.")
public record AssistantSettingsResponseDto(
        String personalInstructions, String writingStyle, String aiOutputLanguage) {

    public static AssistantSettingsResponseDto from(AssistantWritingProfile profile) {
        return new AssistantSettingsResponseDto(
                profile.personalInstructions(), profile.writingStyle(), profile.aiOutputLanguage());
    }
}
