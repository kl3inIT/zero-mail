package com.zeromail.api.dto.admin.rulecatalog;

import com.zeromail.core.rules.catalog.projection.RuleActionDescriptorAdminView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"actions"})
public record RuleCatalogActionsAdminResponse(
        List<RuleCatalogActionDescriptorAdminResponse> actions) {

    public RuleCatalogActionsAdminResponse {
        actions = List.copyOf(actions);
    }

    public static RuleCatalogActionsAdminResponse from(
            List<RuleActionDescriptorAdminView> actionDescriptors) {
        return new RuleCatalogActionsAdminResponse(
                actionDescriptors.stream()
                        .map(RuleCatalogActionDescriptorAdminResponse::from)
                        .toList());
    }
}
