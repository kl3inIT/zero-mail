package com.zeromail.core.llm.gateway.springai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SemanticIntentResponse(
        @JsonProperty(required = true, value = "nodeMatches") List<NodeMatch> nodeMatches) {

    public record NodeMatch(
            @JsonProperty(required = true, value = "nodeId") String nodeId,
            @JsonProperty(required = true, value = "matches") boolean matches) {}
}
