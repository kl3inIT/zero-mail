package com.zeromail.api.dto.rules;

import java.util.Map;

import com.zeromail.core.rules.application.RulePreviewResult;

public record ImpactSummaryResponse(
    int sampleSize,
    int sampledMessageCount,
    int matchedCount,
    Map<String, Integer> proposedActionCounts,
    int deferredCount,
    int conflictCount,
    boolean noWriteNotice,
    String noWriteNoticeKey) {

  static ImpactSummaryResponse from(RulePreviewResult.ImpactSummary impactSummary) {
    return new ImpactSummaryResponse(
        impactSummary.sampleSize(),
        impactSummary.sampledMessageCount(),
        impactSummary.matchedCount(),
        impactSummary.proposedActionCounts(),
        impactSummary.deferredCount(),
        impactSummary.conflictCount(),
        impactSummary.noWriteNotice(),
        impactSummary.noWriteNoticeKey());
  }

  public ImpactSummaryResponse {
    proposedActionCounts = Map.copyOf(proposedActionCounts);
  }
}
