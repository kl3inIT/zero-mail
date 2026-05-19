package com.zeromail.api.dto.llm;

import com.zeromail.core.llm.usecases.ByokValidateResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"ok", "models", "reason"})
public record ByokValidateResponse(
        boolean ok,
        @Schema(nullable = true) List<String> models,
        @Schema(nullable = true) String reason) {

    public ByokValidateResponse {
        models = models == null ? null : List.copyOf(models);
    }

    public static ByokValidateResponse from(ByokValidateResult result) {
        return new ByokValidateResponse(result.ok(), result.models(), result.reason());
    }
}
