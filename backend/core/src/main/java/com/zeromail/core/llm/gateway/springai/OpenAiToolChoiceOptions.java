package com.zeromail.core.llm.gateway.springai;

import com.openai.models.chat.completions.ChatCompletionToolChoiceOption;

final class OpenAiToolChoiceOptions {

    private OpenAiToolChoiceOptions() {}

    static ChatCompletionToolChoiceOption required() {
        return ChatCompletionToolChoiceOption.ofAuto(ChatCompletionToolChoiceOption.Auto.REQUIRED);
    }
}
