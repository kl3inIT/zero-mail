package com.zeromail.api.dto.admin.rulecatalog;

import com.zeromail.core.rules.catalog.projection.RuleExamplePromptAdminView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(
        requiredProperties = {
            "exampleId",
            "exampleTextEn",
            "exampleTextVi",
            "displayOrder",
            "enabled"
        })
public record RuleCatalogExampleAdminResponse(
        UUID exampleId,
        String exampleTextEn,
        String exampleTextVi,
        int displayOrder,
        boolean enabled) {

    public static RuleCatalogExampleAdminResponse from(
            RuleExamplePromptAdminView ruleExamplePromptAdminView) {
        return new RuleCatalogExampleAdminResponse(
                ruleExamplePromptAdminView.promptId(),
                ruleExamplePromptAdminView.exampleTextEn(),
                ruleExamplePromptAdminView.exampleTextVi(),
                ruleExamplePromptAdminView.displayOrder(),
                ruleExamplePromptAdminView.enabled());
    }
}
