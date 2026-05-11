package com.zeromail.core.llm.application;

import java.util.List;

public record ByokValidateResult(boolean ok, List<String> models, String reason) {

    public ByokValidateResult {
        models = models == null ? null : List.copyOf(models);
    }
}
