package com.zeromail.api.dto.settings;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VoiceSettingsUpdateRequest(
        @Size(min = 1, max = 4000) String writingStyle,
        @Size(max = 2000) String personalInstructions,
        @Size(max = 500) String emailSignature,
        @Pattern(regexp = "^(vi|en)$") @Schema(allowableValues = {"vi", "en"})
                String aiOutputLanguage) {}
