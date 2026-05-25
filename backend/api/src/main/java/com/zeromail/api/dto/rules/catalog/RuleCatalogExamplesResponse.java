package com.zeromail.api.dto.rules.catalog;

import com.zeromail.core.rules.catalog.projection.RuleExamplePersonaView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"personas"})
public record RuleCatalogExamplesResponse(List<RuleCatalogPersonaResponse> personas) {

    public RuleCatalogExamplesResponse {
        personas = List.copyOf(personas);
    }

    public static RuleCatalogExamplesResponse from(List<RuleExamplePersonaView> personas) {
        return new RuleCatalogExamplesResponse(
                personas.stream().map(RuleCatalogPersonaResponse::from).toList());
    }
}
