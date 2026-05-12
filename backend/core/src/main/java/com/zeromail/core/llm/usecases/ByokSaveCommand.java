package com.zeromail.core.llm.usecases;

import com.zeromail.core.llm.domain.ByokProviderPreset;
import java.util.Objects;

public record ByokSaveCommand(
        ByokProviderPreset preset, String endpoint, String model, String apiKey) {

    public ByokSaveCommand {
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(apiKey, "apiKey");
    }
}
