package com.zeromail.core.llm.gateway.springai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientAttributes;

public final class SpringAiRawToolCallSupport {

    private SpringAiRawToolCallSupport() {}

    public static void preserveRawToolCalls(ChatClient.AdvisorSpec advisorSpec) {
        advisorSpec.param(ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER.getKey(), false);
    }
}
