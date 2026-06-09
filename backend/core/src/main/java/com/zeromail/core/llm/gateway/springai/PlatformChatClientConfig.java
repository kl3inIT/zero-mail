package com.zeromail.core.llm.gateway.springai;

import com.zeromail.core.llm.config.LlmProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PlatformChatClientConfig {

    @Bean
    OpenAiChatModel platformOpenAiChatModel(LlmProperties llmConfiguration) {
        LlmProperties.PlatformProperties llmProperties = llmConfiguration.platform();
        return OpenAiChatModel.builder()
                .options(
                        OpenAiChatOptions.builder()
                                .baseUrl(llmProperties.baseUrl())
                                .apiKey(llmProperties.apiKey())
                                .model(llmProperties.compileModel())
                                .temperature(0.0)
                                .timeout(llmProperties.readTimeout())
                                .build())
                .build();
    }

    @Bean
    ChatClient platformChatClient(OpenAiChatModel platformOpenAiChatModel) {
        return ChatClient.create(platformOpenAiChatModel);
    }
}
