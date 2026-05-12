package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.usecases.RulePreviewResult;
import java.time.Instant;
import java.util.List;

public record PreviewRowResponse(
        String gmailMessageId,
        String gmailThreadId,
        String sanitizedSenderEmail,
        String sanitizedSenderDomain,
        String sanitizedSubjectExcerpt,
        Instant internalDate,
        List<String> gmailLabelIds,
        boolean matched,
        List<ActionChipResponse> proposedActionChips,
        List<EvidenceChipResponse> matchedEvidenceChips,
        List<EvidenceChipResponse> deferredEvidenceChips,
        List<ConflictChipResponse> conflictChips) {

    static PreviewRowResponse from(RulePreviewResult.PreviewRow previewRow) {
        return new PreviewRowResponse(
                previewRow.gmailMessageId(),
                previewRow.gmailThreadId(),
                previewRow.sanitizedSenderEmail(),
                previewRow.sanitizedSenderDomain(),
                previewRow.sanitizedSubjectExcerpt(),
                previewRow.internalDate(),
                previewRow.gmailLabelIds(),
                previewRow.matched(),
                previewRow.proposedActionChips().stream().map(ActionChipResponse::from).toList(),
                previewRow.matchedEvidenceChips().stream().map(EvidenceChipResponse::from).toList(),
                previewRow.deferredEvidenceChips().stream()
                        .map(EvidenceChipResponse::from)
                        .toList(),
                previewRow.conflictChips().stream().map(ConflictChipResponse::from).toList());
    }

    public PreviewRowResponse {
        gmailLabelIds = List.copyOf(gmailLabelIds);
        proposedActionChips = List.copyOf(proposedActionChips);
        matchedEvidenceChips = List.copyOf(matchedEvidenceChips);
        deferredEvidenceChips = List.copyOf(deferredEvidenceChips);
        conflictChips = List.copyOf(conflictChips);
    }
}
