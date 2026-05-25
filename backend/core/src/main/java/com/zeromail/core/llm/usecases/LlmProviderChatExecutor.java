package com.zeromail.core.llm.usecases;

public interface LlmProviderChatExecutor {

    LlmChatResult call(LlmProviderCredential credential, LlmChatRequest request);
}
