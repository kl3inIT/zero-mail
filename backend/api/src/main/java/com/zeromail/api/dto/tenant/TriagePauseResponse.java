package com.zeromail.api.dto.tenant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = "paused")
public record TriagePauseResponse(boolean paused) {

    public static TriagePauseResponse from(boolean paused) {
        return new TriagePauseResponse(paused);
    }
}
