package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.usecases.RuleCustomPreviewResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(requiredProperties = "entries")
public record RuleCustomPreviewResponse(List<Entry> entries) {

    public static RuleCustomPreviewResponse from(RuleCustomPreviewResult previewResult) {
        return new RuleCustomPreviewResponse(
                previewResult.entries().stream().map(Entry::from).toList());
    }

    public RuleCustomPreviewResponse {
        entries = List.copyOf(entries);
    }

    @Schema(
            requiredProperties = {
                "ruleId",
                "displayName",
                "enabled",
                "matched",
                "deferred",
                "proposedActionChips",
                "matchedEvidenceChips",
                "deferredEvidenceChips"
            })
    public record Entry(
            UUID ruleId,
            String displayName,
            boolean enabled,
            boolean matched,
            boolean deferred,
            List<ActionChipResponse> proposedActionChips,
            List<EvidenceChipResponse> matchedEvidenceChips,
            List<EvidenceChipResponse> deferredEvidenceChips) {

        static Entry from(RuleCustomPreviewResult.Entry entry) {
            return new Entry(
                    entry.ruleId(),
                    entry.displayName(),
                    entry.enabled(),
                    entry.matched(),
                    entry.deferred(),
                    entry.proposedActionChips().stream().map(ActionChipResponse::from).toList(),
                    entry.matchedEvidenceChips().stream().map(EvidenceChipResponse::from).toList(),
                    entry.deferredEvidenceChips().stream()
                            .map(EvidenceChipResponse::from)
                            .toList());
        }

        public Entry {
            proposedActionChips = List.copyOf(proposedActionChips);
            matchedEvidenceChips = List.copyOf(matchedEvidenceChips);
            deferredEvidenceChips = List.copyOf(deferredEvidenceChips);
        }
    }
}
