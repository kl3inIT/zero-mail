package com.zeromail.core.llm.usecases;

import com.zeromail.core.llm.routing.PlatformLlmRouteCredentials;

/** Vendor-neutral seam between the gateway service layer and the Spring AI adapter. */
public interface LlmModelClient {

    LlmChatResult call(LlmChatRequest request);

    default LlmChatResult call(
            LlmChatRequest request, PlatformLlmRouteCredentials routeCredentials) {
        return call(request);
    }
}
