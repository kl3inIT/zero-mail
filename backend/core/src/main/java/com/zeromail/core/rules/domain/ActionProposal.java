package com.zeromail.core.rules.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ActionProposal(
        ActionIntent actionIntent,
        List<UUID> contributingRuleIds,
        List<String> contributingRuleNames,
        List<String> evidenceIds) {

    public ActionProposal {
        Objects.requireNonNull(actionIntent, "actionIntent must not be null");
        contributingRuleIds =
                List.copyOf(
                        Objects.requireNonNull(
                                contributingRuleIds, "contributingRuleIds must not be null"));
        contributingRuleNames =
                List.copyOf(
                        Objects.requireNonNull(
                                contributingRuleNames, "contributingRuleNames must not be null"));
        evidenceIds =
                List.copyOf(Objects.requireNonNull(evidenceIds, "evidenceIds must not be null"));
        if (contributingRuleIds.isEmpty()) {
            throw new IllegalArgumentException("contributingRuleIds must not be empty");
        }
        if (contributingRuleNames.isEmpty()) {
            throw new IllegalArgumentException("contributingRuleNames must not be empty");
        }
    }

    public RuleActionType type() {
        return actionIntent.type();
    }

    public ActionProposal mergeDuplicate(ActionProposal duplicateProposal) {
        if (!actionIntent.equals(duplicateProposal.actionIntent())) {
            throw new IllegalArgumentException("Only exact duplicate action intents can be merged");
        }

        return new ActionProposal(
                actionIntent,
                appendUnique(contributingRuleIds, duplicateProposal.contributingRuleIds()),
                appendAll(contributingRuleNames, duplicateProposal.contributingRuleNames()),
                appendUnique(evidenceIds, duplicateProposal.evidenceIds()));
    }

    private static <T> List<T> appendUnique(List<T> existingValues, List<T> additionalValues) {
        ArrayList<T> mergedValues = new ArrayList<>(existingValues);
        for (T additionalValue : additionalValues) {
            if (!mergedValues.contains(additionalValue)) {
                mergedValues.add(additionalValue);
            }
        }
        return mergedValues;
    }

    private static <T> List<T> appendAll(List<T> existingValues, List<T> additionalValues) {
        ArrayList<T> mergedValues = new ArrayList<>(existingValues);
        mergedValues.addAll(additionalValues);
        return mergedValues;
    }
}
