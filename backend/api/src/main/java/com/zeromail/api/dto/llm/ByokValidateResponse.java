package com.zeromail.api.dto.llm;

import com.zeromail.core.llm.application.ByokValidateResult;
import java.util.List;

public record ByokValidateResponse(boolean ok, List<String> models, String reason) {

    public ByokValidateResponse {
        models = models == null ? null : List.copyOf(models);
    }

    public static ByokValidateResponse from(ByokValidateResult result) {
        return new ByokValidateResponse(result.ok(), result.models(), result.reason());
    }
}
