package com.zeromail.core.llm.gateway.springai;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import com.zeromail.core.llm.byok.ByokEndpointValidator;
import com.zeromail.core.llm.model.LlmChatRequest;
import com.zeromail.core.llm.model.LlmChatResult;
import com.zeromail.core.llm.model.LlmTool;
import com.zeromail.core.llm.model.LlmUsage;
import com.zeromail.core.llm.model.RawToolCall;
import com.zeromail.core.llm.service.ByokLlmModelClient;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class OpenAiCompatibleByokModelClient implements ByokLlmModelClient {

  private final ByokEndpointValidator byokEndpointValidator;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public OpenAiCompatibleByokModelClient(ByokEndpointValidator byokEndpointValidator) {
    this.byokEndpointValidator = byokEndpointValidator;
  }

  @Override
  public LlmChatResult call(byte[] decryptedKey, String endpoint, LlmChatRequest request) {
    String canonicalEndpoint = byokEndpointValidator.validateOpenAiCompatible(endpoint);
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
                      .internalToolExecutionEnabled(false)
                      .build())
              .build();
      derivedChatClient = ChatClient.create(derivedModel);
      ChatResponse chatResponse =
          derivedChatClient
              .prompt()
              .system(request.systemPrompt())
              .user(request.userMessage())
              .toolCallbacks(translateTools(request.tools()))
              .options(chatOptions(request))
              .call()
              .chatResponse();
      if (chatResponse == null) {
        throw new IllegalStateException("No chat response returned");
      }
      return toLlmChatResult(chatResponse);
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
            .internalToolExecutionEnabled(false);
    if (request.toolChoiceRequired()) {
      chatOptionsBuilder.toolChoice("required");
    }
    return chatOptionsBuilder;
  }

  private List<ToolCallback> translateTools(List<LlmTool> tools) {
    return tools.stream().map(this::toToolCallback).toList();
  }

  private ToolCallback toToolCallback(LlmTool tool) {
    return FunctionToolCallback.builder(tool.name(), (Map<String, Object> toolInput) -> Map.of())
        .description(tool.description())
        .inputSchema(toJsonSchema(tool))
        .inputType(Map.class)
        .build();
  }

  private String toJsonSchema(LlmTool tool) {
    try {
      return objectMapper.writeValueAsString(tool.jsonSchema());
    } catch (JacksonException jsonSerializationFailure) {
      throw new IllegalStateException(
          "Unable to serialize LLM tool schema", jsonSerializationFailure);
    }
  }

  private LlmChatResult toLlmChatResult(ChatResponse chatResponse) {
    Generation generation = chatResponse.getResult();
    if (generation == null) {
      throw new IllegalStateException("No chat generation returned");
    }
    AssistantMessage assistantMessage = generation.getOutput();
    List<RawToolCall> rawToolCalls =
        assistantMessage.getToolCalls().stream()
            .map(toolCall -> new RawToolCall(toolCall.name(), toolCall.arguments()))
            .toList();
    Usage usage = chatResponse.getMetadata().getUsage();
    return new LlmChatResult(
        rawToolCalls,
        new LlmUsage(
            tokenCount(usage == null ? null : usage.getPromptTokens()),
            tokenCount(usage == null ? null : usage.getCompletionTokens()),
            generation.getMetadata().getFinishReason()));
  }

  private int tokenCount(Integer tokenCount) {
    return tokenCount == null ? 0 : tokenCount;
  }
}
