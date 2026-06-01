package com.zeromail.api.dto.settings;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BehaviorSettingsUpdateRequest(
        Boolean autoDraftReplies,
        @Pattern(regexp = "^(LOW|MEDIUM|HIGH)$")
                @Schema(allowableValues = {"LOW", "MEDIUM", "HIGH"})
                String draftConfidence,
        Boolean sensitiveDataProtection) {}
