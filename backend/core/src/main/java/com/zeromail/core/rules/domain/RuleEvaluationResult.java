package com.zeromail.core.rules.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RuleEvaluationResult(
    MatcherEvaluationState status,
    List<String> matchedEvidenceIds,
    List<String> deferredEvidenceIds,
    Map<String, String> evidenceById) {

  public RuleEvaluationResult {
    Objects.requireNonNull(status, "status must not be null");
    matchedEvidenceIds =
        List.copyOf(Objects.requireNonNull(matchedEvidenceIds, "matchedEvidenceIds must not be null"));
    deferredEvidenceIds =
        List.copyOf(
            Objects.requireNonNull(deferredEvidenceIds, "deferredEvidenceIds must not be null"));
    evidenceById =
        Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNull(evidenceById, "evidenceById must not be null")));
  }

  public static RuleEvaluationResult matched(String matcherNodeId, String evidenceReason) {
    return terminal(MatcherEvaluationState.MATCHED, matcherNodeId, evidenceReason);
  }

  public static RuleEvaluationResult notMatched(String matcherNodeId, String evidenceReason) {
    return terminal(MatcherEvaluationState.NOT_MATCHED, matcherNodeId, evidenceReason);
  }

  public static RuleEvaluationResult deferred(String matcherNodeId, String evidenceReason) {
    LinkedHashMap<String, String> evidenceById = new LinkedHashMap<>();
    evidenceById.put(matcherNodeId, evidenceReason);
    return new RuleEvaluationResult(
        MatcherEvaluationState.DEFERRED, List.of(), List.of(matcherNodeId), evidenceById);
  }

  public static RuleEvaluationResult compose(
      MatcherEvaluationState status, List<RuleEvaluationResult> childResults) {
    ArrayList<String> matchedEvidenceIds = new ArrayList<>();
    ArrayList<String> deferredEvidenceIds = new ArrayList<>();
    LinkedHashMap<String, String> evidenceById = new LinkedHashMap<>();

    for (RuleEvaluationResult childResult : childResults) {
      matchedEvidenceIds.addAll(childResult.matchedEvidenceIds());
      deferredEvidenceIds.addAll(childResult.deferredEvidenceIds());
      evidenceById.putAll(childResult.evidenceById());
    }

    return new RuleEvaluationResult(status, matchedEvidenceIds, deferredEvidenceIds, evidenceById);
  }

  private static RuleEvaluationResult terminal(
      MatcherEvaluationState status, String matcherNodeId, String evidenceReason) {
    LinkedHashMap<String, String> evidenceById = new LinkedHashMap<>();
    evidenceById.put(matcherNodeId, evidenceReason);
    List<String> matchedEvidenceIds =
        status == MatcherEvaluationState.MATCHED ? List.of(matcherNodeId) : List.of();
    return new RuleEvaluationResult(status, matchedEvidenceIds, List.of(), evidenceById);
  }
}
