package com.zeromail.core.rules.usecases;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RuleCustomPreviewResult(List<Entry> entries) {

    public RuleCustomPreviewResult {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries must not be null"));
    }

    public record Entry(
            UUID ruleId,
            String displayName,
            boolean enabled,
            boolean matched,
            boolean deferred,
            List<RulePreviewResult.ActionChip> proposedActionChips,
            List<RulePreviewResult.EvidenceChip> matchedEvidenceChips,
            List<RulePreviewResult.EvidenceChip> deferredEvidenceChips) {

        public Entry {
            Objects.requireNonNull(ruleId, "ruleId must not be null");
            Objects.requireNonNull(displayName, "displayName must not be null");
            proposedActionChips =
                    List.copyOf(
                            Objects.requireNonNull(
                                    proposedActionChips, "proposedActionChips must not be null"));
            matchedEvidenceChips =
                    List.copyOf(
                            Objects.requireNonNull(
                                    matchedEvidenceChips, "matchedEvidenceChips must not be null"));
            deferredEvidenceChips =
                    List.copyOf(
                            Objects.requireNonNull(
                                    deferredEvidenceChips,
                                    "deferredEvidenceChips must not be null"));
        }
    }
}
