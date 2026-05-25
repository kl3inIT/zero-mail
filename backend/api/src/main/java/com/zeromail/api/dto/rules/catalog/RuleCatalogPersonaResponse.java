package com.zeromail.api.dto.rules.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.rules.catalog.projection.RuleExamplePersonaView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"personaId", "personaKey", "displayName", "displayOrder", "examples"})
public record RuleCatalogPersonaResponse(
        UUID personaId,
        String personaKey,
        String displayName,
        String icon,
        int displayOrder,
        List<RuleCatalogExampleResponse> examples) {

    public RuleCatalogPersonaResponse {
        examples = List.copyOf(examples);
    }

    public static RuleCatalogPersonaResponse from(RuleExamplePersonaView ruleExamplePersonaView) {
        return new RuleCatalogPersonaResponse(
                ruleExamplePersonaView.personaId(),
                ruleExamplePersonaView.personaKey(),
                ruleExamplePersonaView.displayName(),
                ruleExamplePersonaView.icon(),
                ruleExamplePersonaView.displayOrder(),
                ruleExamplePersonaView.prompts().stream()
                        .map(RuleCatalogExampleResponse::from)
                        .toList());
    }
}
