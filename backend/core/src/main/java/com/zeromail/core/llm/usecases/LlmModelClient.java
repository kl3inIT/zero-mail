package com.zeromail.core.llm.usecases;

/** Vendor-neutral seam between the gateway service layer and the Spring AI adapter. */
public interface LlmModelClient {

    LlmChatResult call(LlmChatRequest request);
}
