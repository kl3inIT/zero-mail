package com.zeromail.core.llm.service;

import com.zeromail.core.llm.usecases.LlmChatRequest;
import com.zeromail.core.llm.usecases.LlmChatResult;

/** Vendor-neutral seam between the gateway service layer and the Spring AI adapter. */
public interface LlmModelClient {

    LlmChatResult call(LlmChatRequest request);
}
