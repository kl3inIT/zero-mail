package com.zeromail.core.llm.byok;

import java.time.Instant;
import java.util.List;

public record ByokRowSummary(
        String provider,
        String baseUrl,
        String lastFourChars,
        String modelId,
        boolean active,
        String lastTestResult,
        Instant lastTestedAt,
        List<String> lastTestModels) {

    public ByokRowSummary {
        lastTestModels = lastTestModels == null ? List.of() : List.copyOf(lastTestModels);
    }
}
