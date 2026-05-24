package com.zeromail.api.dto.rules.catalog;

import com.zeromail.core.rules.catalog.projection.RuleExamplePromptView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(requiredProperties = {"exampleId", "exampleText", "displayOrder"})
public record RuleCatalogExampleResponse(UUID exampleId, String exampleText, int displayOrder) {

    public static RuleCatalogExampleResponse from(RuleExamplePromptView ruleExamplePromptView) {
        return new RuleCatalogExampleResponse(
                ruleExamplePromptView.promptId(),
                ruleExamplePromptView.exampleText(),
                ruleExamplePromptView.displayOrder());
    }
}
