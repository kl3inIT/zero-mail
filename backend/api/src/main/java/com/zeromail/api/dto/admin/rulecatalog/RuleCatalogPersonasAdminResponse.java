package com.zeromail.api.dto.admin.rulecatalog;

import com.zeromail.core.rules.catalog.projection.RuleExamplePersonaAdminView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"personas"})
public record RuleCatalogPersonasAdminResponse(List<RuleCatalogPersonaAdminResponse> personas) {

    public RuleCatalogPersonasAdminResponse {
        personas = List.copyOf(personas);
    }

    public static RuleCatalogPersonasAdminResponse from(
            List<RuleExamplePersonaAdminView> personas) {
        return new RuleCatalogPersonasAdminResponse(
                personas.stream().map(RuleCatalogPersonaAdminResponse::from).toList());
    }
}
