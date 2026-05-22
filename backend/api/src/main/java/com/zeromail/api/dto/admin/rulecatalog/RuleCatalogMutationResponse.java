package com.zeromail.api.dto.admin.rulecatalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"status"})
public record RuleCatalogMutationResponse(String status, UUID targetId, String targetKey) {

    public static RuleCatalogMutationResponse created(UUID targetId) {
        return new RuleCatalogMutationResponse("created", targetId, null);
    }
}
