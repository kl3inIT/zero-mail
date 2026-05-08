package com.zeromail.core.llm.gateway.springai;

import java.nio.charset.StandardCharsets;

import com.google.genai.Client;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Component;

import com.zeromail.core.llm.byok.ByokEndpointValidator;
import com.zeromail.core.llm.model.LlmChatRequest;
import com.zeromail.core.llm.model.LlmChatResult;
import com.zeromail.core.llm.service.ByokLlmModelClient;

@Component
public class GoogleGenAiByokModelClient implements ByokLlmModelClient {

  private final ByokEndpointValidator byokEndpointValidator;
  private final SpringAiByokChatSupport chatSupport = new SpringAiByokChatSupport();

  public GoogleGenAiByokModelClient(ByokEndpointValidator byokEndpointValidator) {
    this.byokEndpointValidator = byokEndpointValidator;
  }

  @Override
  public LlmChatResult call(byte[] decryptedKey, String endpoint, LlmChatRequest request) {
    byokEndpointValidator.validateGoogleGenAi(endpoint);
    String plaintextApiKey = new String(decryptedKey, StandardCharsets.UTF_8);
    GoogleGenAiChatModel derivedModel = null;
    ChatClient derivedChatClient = null;
    try {
      Client genAiClient = Client.builder().apiKey(plaintextApiKey).build();
      derivedModel =
          GoogleGenAiChatModel.builder()
              .genAiClient(genAiClient)
              .defaultOptions(
                  GoogleGenAiChatOptions.builder()
                      .model(request.model())
                      .temperature(request.temperature())
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

  private GoogleGenAiChatOptions.Builder chatOptions(LlmChatRequest request) {
    return GoogleGenAiChatOptions.builder()
        .model(request.model())
        .temperature(request.temperature())
        .internalToolExecutionEnabled(false);
  }
}
