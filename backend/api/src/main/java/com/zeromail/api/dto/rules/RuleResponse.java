package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.projection.RuleStatusProjection;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(
        requiredProperties = {
            "ruleId",
            "displayName",
            "sourceText",
            "enabled",
            "orderIndex",
            "sourceLanguage",
            "schemaVersion",
            "matcherAst",
            "actionIntents",
            "entityVersion",
            "lastPreviewedEntityVersion",
            "lastPreviewedAt",
            "templateKey",
            "templateVersion",
            "customized"
        })
public record RuleResponse(
        UUID ruleId,
        String displayName,
        String sourceText,
        boolean enabled,
        int orderIndex,
        String sourceLanguage,
        String schemaVersion,
        String matcherAst,
        String actionIntents,
        Integer entityVersion,
        @Schema(nullable = true) Integer lastPreviewedEntityVersion,
        @Schema(nullable = true) Instant lastPreviewedAt,
        @Schema(nullable = true) String templateKey,
        @Schema(nullable = true) Integer templateVersion,
        boolean customized) {

    public static RuleResponse from(RuleStatusProjection ruleStatusProjection) {
        return new RuleResponse(
                ruleStatusProjection.ruleId().value(),
                ruleStatusProjection.displayName(),
                ruleStatusProjection.sourceText(),
                ruleStatusProjection.enabled(),
                ruleStatusProjection.orderIndex(),
                ruleStatusProjection.sourceLanguage().id(),
                ruleStatusProjection.schemaVersion().id(),
                ruleStatusProjection.matcherAst(),
                ruleStatusProjection.actionIntents(),
                ruleStatusProjection.entityVersion(),
                ruleStatusProjection.lastPreviewedEntityVersion(),
                ruleStatusProjection.lastPreviewedAt(),
                ruleStatusProjection.templateKey(),
                ruleStatusProjection.templateVersion(),
                ruleStatusProjection.customized());
    }
}
