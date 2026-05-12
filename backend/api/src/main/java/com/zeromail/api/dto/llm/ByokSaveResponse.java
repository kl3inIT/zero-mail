package com.zeromail.api.dto.llm;

import com.zeromail.core.llm.usecases.ByokSaveResult;
import java.time.Instant;

public record ByokSaveResponse(boolean ok, Instant savedAt) {

    public static ByokSaveResponse from(ByokSaveResult result) {
        return new ByokSaveResponse(result.ok(), result.savedAt());
    }
}
