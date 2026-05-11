package com.zeromail.api.dto.tenant;

public record TriagePauseResponse(boolean paused) {

    public static TriagePauseResponse from(boolean paused) {
        return new TriagePauseResponse(paused);
    }
}
