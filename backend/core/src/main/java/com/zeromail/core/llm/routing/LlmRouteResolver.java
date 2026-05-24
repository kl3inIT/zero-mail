package com.zeromail.core.llm.routing;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public interface LlmRouteResolver {

    List<ResolvedLlmRoute> resolve(LlmRuntimeTask task);

    default Optional<ResolvedLlmRoute> resolvePrimary(LlmRuntimeTask task) {
        Objects.requireNonNull(task, "task");
        List<ResolvedLlmRoute> routes = resolve(task);
        return routes.isEmpty() ? Optional.empty() : Optional.of(routes.get(0));
    }
}
