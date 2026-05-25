package com.zeromail.api.dto.admin.rulecatalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.rules.catalog.projection.RuleExamplePersonaAdminView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
        requiredProperties = {
            "personaId",
            "personaKey",
            "displayNameEn",
            "displayNameVi",
            "displayOrder",
            "enabled",
            "examples"
        })
public record RuleCatalogPersonaAdminResponse(
        UUID personaId,
        String personaKey,
        String displayNameEn,
        String displayNameVi,
        String icon,
        int displayOrder,
        boolean enabled,
        List<RuleCatalogExampleAdminResponse> examples) {

    public RuleCatalogPersonaAdminResponse {
        examples = List.copyOf(examples);
    }

    public static RuleCatalogPersonaAdminResponse from(
            RuleExamplePersonaAdminView ruleExamplePersonaAdminView) {
        return new RuleCatalogPersonaAdminResponse(
                ruleExamplePersonaAdminView.personaId(),
                ruleExamplePersonaAdminView.personaKey(),
                ruleExamplePersonaAdminView.displayNameEn(),
                ruleExamplePersonaAdminView.displayNameVi(),
                ruleExamplePersonaAdminView.icon(),
                ruleExamplePersonaAdminView.displayOrder(),
                ruleExamplePersonaAdminView.enabled(),
                ruleExamplePersonaAdminView.prompts().stream()
                        .map(RuleCatalogExampleAdminResponse::from)
                        .toList());
    }
}
