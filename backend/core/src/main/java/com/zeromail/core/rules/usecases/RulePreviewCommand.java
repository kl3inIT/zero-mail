package com.zeromail.core.rules.usecases;

import com.zeromail.core.rules.domain.ActionIntent;
import com.zeromail.core.rules.domain.MatcherNode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RulePreviewCommand(
        UUID tenantId,
        UUID gmailConnectionId,
        UUID ruleId,
        MatcherNode matcherNode,
        List<ActionIntent> actionIntents,
        Integer requestedSampleSize,
        boolean evaluateSemanticIntents) {

    public RulePreviewCommand {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        actionIntents = actionIntents == null ? List.of() : List.copyOf(actionIntents);
        boolean savedRulePreview = ruleId != null;
        boolean draftPreview =
                gmailConnectionId != null && matcherNode != null && !actionIntents.isEmpty();
        if (savedRulePreview == draftPreview) {
            throw new IllegalArgumentException(
                    "Preview command must target either a saved rule or a draft matcher/action payload");
        }
    }

    public static RulePreviewCommand savedRule(
            UUID tenantId, UUID ruleId, Integer requestedSampleSize) {
        return savedRule(tenantId, ruleId, requestedSampleSize, false);
    }

    public static RulePreviewCommand savedRule(
            UUID tenantId,
            UUID ruleId,
            Integer requestedSampleSize,
            boolean evaluateSemanticIntents) {
        return new RulePreviewCommand(
                tenantId,
                null,
                ruleId,
                null,
                List.of(),
                requestedSampleSize,
                evaluateSemanticIntents);
    }

    public static RulePreviewCommand draft(
            UUID tenantId,
            UUID gmailConnectionId,
            MatcherNode matcherNode,
            List<ActionIntent> actionIntents,
            Integer requestedSampleSize) {
        return draft(
                tenantId,
                gmailConnectionId,
                matcherNode,
                actionIntents,
                requestedSampleSize,
                false);
    }

    public static RulePreviewCommand draft(
            UUID tenantId,
            UUID gmailConnectionId,
            MatcherNode matcherNode,
            List<ActionIntent> actionIntents,
            Integer requestedSampleSize,
            boolean evaluateSemanticIntents) {
        return new RulePreviewCommand(
                tenantId,
                gmailConnectionId,
                null,
                matcherNode,
                actionIntents,
                requestedSampleSize,
                evaluateSemanticIntents);
    }

    public boolean savedRulePreview() {
        return ruleId != null;
    }
}
