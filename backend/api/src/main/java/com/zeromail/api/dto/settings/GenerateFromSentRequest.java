package com.zeromail.api.dto.settings;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record GenerateFromSentRequest(
        @Min(1) @Max(50) @Schema(defaultValue = "20", minimum = "1", maximum = "50")
                Integer sampleSize) {

    public int sampleSizeOrDefault() {
        return sampleSize == null ? 20 : sampleSize;
    }
}
