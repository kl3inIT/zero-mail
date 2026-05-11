package com.zeromail.core.llm.gateway.springai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SemanticIntentResponse(
    @JsonProperty(required = true, value = "nodeMatches") List<NodeMatch> nodeMatches) {

  public record NodeMatch(
      @JsonProperty(required = true, value = "nodeId") String nodeId,
      @JsonProperty(required = true, value = "matches") boolean matches) {}
}
