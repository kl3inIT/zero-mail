package com.zeromail.core.llm.gateway.springai;

import java.nio.charset.StandardCharsets;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import com.zeromail.core.config.ZeroMailCoreProperties;
import com.zeromail.core.config.ZeroMailCoreProperties.ZeroMailLlmByokProperties;
import com.zeromail.core.llm.byok.ByokEndpointValidator;
import com.zeromail.core.llm.application.LlmChatRequest;
import com.zeromail.core.llm.application.LlmChatResult;
import com.zeromail.core.llm.service.ByokLlmModelClient;

@Component
public class OpenAiByokModelClient implements ByokLlmModelClient {

  private final ByokEndpointValidator byokEndpointValidator;
  private final ZeroMailLlmByokProperties byokProperties;
  private final SpringAiByokChatSupport chatSupport = new SpringAiByokChatSupport();

  public OpenAiByokModelClient(
      ByokEndpointValidator byokEndpointValidator, ZeroMailCoreProperties zeroMailCoreProperties) {
    this.byokEndpointValidator = byokEndpointValidator;
    this.byokProperties = zeroMailCoreProperties.llm().byok();
  }

  @Override
  public LlmChatResult call(byte[] decryptedKey, String endpoint, LlmChatRequest request) {
    String canonicalEndpoint = byokEndpointValidator.validateCustomOpenAiCompatible(endpoint);
    String plaintextApiKey = new String(decryptedKey, StandardCharsets.UTF_8);
    OpenAiChatModel derivedModel = null;
    ChatClient derivedChatClient = null;
    try {
      derivedModel =
          OpenAiChatModel.builder()
              .options(
                  OpenAiChatOptions.builder()
                      .apiKey(plaintextApiKey)
                      .baseUrl(canonicalEndpoint)
                      .model(request.model())
                      .temperature(request.temperature())
                      .timeout(byokProperties.readTimeout())
                      .internalToolExecutionEnabled(false)
                      .build())
              .build();
      derivedChatClient = ChatClient.create(derivedModel);
      ChatResponse chatResponse =
          derivedChatClient
              .prompt()
              .system(request.systemPrompt())
              .user(request.userMessage())
              .toolCallbacks(chatSupport.translateTools(request.tools()))
              .options(chatOptions(request))
              .call()
              .chatResponse();
      if (chatResponse == null) {
        throw new IllegalStateException("No chat response returned");
      }
      return chatSupport.toLlmChatResult(chatResponse);
    } finally {
      plaintextApiKey = null;
      derivedChatClient = null;
      derivedModel = null;
    }
  }

  private OpenAiChatOptions.Builder chatOptions(LlmChatRequest request) {
    OpenAiChatOptions.Builder chatOptionsBuilder =
        OpenAiChatOptions.builder()
            .model(request.model())
            .temperature(request.temperature())
            .timeout(byokProperties.readTimeout())
            .internalToolExecutionEnabled(false);
    if (request.toolChoiceRequired()) {
      chatOptionsBuilder.toolChoice("required");
    }
    return chatOptionsBuilder;
  }
}
