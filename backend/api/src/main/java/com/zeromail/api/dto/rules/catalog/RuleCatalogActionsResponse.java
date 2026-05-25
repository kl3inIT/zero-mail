package com.zeromail.api.dto.rules.catalog;

import com.zeromail.core.rules.catalog.projection.RuleActionDescriptorView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"actions"})
public record RuleCatalogActionsResponse(List<RuleCatalogActionDescriptorResponse> actions) {

    public RuleCatalogActionsResponse {
        actions = List.copyOf(actions);
    }

    public static RuleCatalogActionsResponse from(
            List<RuleActionDescriptorView> actionDescriptors) {
        return new RuleCatalogActionsResponse(
                actionDescriptors.stream().map(RuleCatalogActionDescriptorResponse::from).toList());
    }
}
