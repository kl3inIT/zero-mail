package com.zeromail.core.llm.routing;

import java.util.Optional;
import java.util.UUID;

/** Resolves operator-owned platform credentials for a runtime LLM route. */
public interface PlatformLlmRouteCredentialResolver {

    Optional<PlatformLlmRouteCredentials> resolve(String providerId, UUID keyId);
}
