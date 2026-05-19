package com.zeromail.api.dto.llm;

import com.zeromail.core.llm.usecases.ByokSaveResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(requiredProperties = {"ok", "savedAt"})
public record ByokSaveResponse(boolean ok, Instant savedAt) {

    public static ByokSaveResponse from(ByokSaveResult result) {
        return new ByokSaveResponse(result.ok(), result.savedAt());
    }
}
