package com.zeromail.core.llm.gateway.springai;

import com.zeromail.core.config.ZeroMailCoreProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PlatformChatClientConfig {

    @Bean
    OpenAiChatModel platformOpenAiChatModel(ZeroMailCoreProperties zeroMailCoreProperties) {
        ZeroMailCoreProperties.ZeroMailLlmProperties llmProperties =
                zeroMailCoreProperties.llm().platform();
        return OpenAiChatModel.builder()
                .options(
                        OpenAiChatOptions.builder()
                                .baseUrl(llmProperties.baseUrl())
                                .apiKey(llmProperties.apiKey())
                                .model(llmProperties.compileModel())
                                .temperature(0.0)
                                .timeout(llmProperties.readTimeout())
                                .internalToolExecutionEnabled(false)
                                .build())
                .build();
    }

    @Bean
    ChatClient platformChatClient(OpenAiChatModel platformOpenAiChatModel) {
        return ChatClient.create(platformOpenAiChatModel);
    }
}
