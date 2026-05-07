package com.zeromail.core.llm.gateway.springai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zeromail.core.config.ZeroMailCoreProperties;

@Configuration
class PlatformChatClientConfig {

  @Bean
  OpenAiApi platformOpenAiApi(
      ZeroMailCoreProperties zeroMailCoreProperties, PlatformApiKey platformApiKey) {
    return OpenAiApi.builder()
        .baseUrl(zeroMailCoreProperties.llm().platform().baseUrl())
        .apiKey(platformApiKey)
        .build();
  }

  @Bean
  OpenAiChatModel platformOpenAiChatModel(OpenAiApi platformOpenAiApi) {
    return OpenAiChatModel.builder()
        .openAiApi(platformOpenAiApi)
        .defaultOptions(
            OpenAiChatOptions.builder()
                .temperature(0.0)
                .internalToolExecutionEnabled(false)
                .build())
        .build();
  }

  @Bean
  ChatClient platformChatClient(OpenAiChatModel platformOpenAiChatModel) {
    return ChatClient.create(platformOpenAiChatModel);
  }
}
