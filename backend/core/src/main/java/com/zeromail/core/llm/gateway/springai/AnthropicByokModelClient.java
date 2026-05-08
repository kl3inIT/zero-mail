package com.zeromail.core.llm.gateway.springai;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.ToolChoice;
import com.anthropic.models.messages.ToolChoiceAny;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
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
public class AnthropicByokModelClient implements ByokLlmModelClient {

  private static final int MAX_TOKENS = 512;

  private final ChatClient parentAnthropicChatClient;
  private final ByokEndpointValidator byokEndpointValidator;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public AnthropicByokModelClient(ByokEndpointValidator byokEndpointValidator) {
    this.parentAnthropicChatClient =
        ChatClient.create(
            AnthropicChatModel.builder()
                .options(
                    AnthropicChatOptions.builder()
                        .apiKey("unused-byok-placeholder")
                        .model(Model.of("claude-3-haiku-20240307"))
                        .maxTokens(MAX_TOKENS)
                        .build())
                .build());
    this.byokEndpointValidator = byokEndpointValidator;
  }

  @Override
  public LlmChatResult call(byte[] decryptedKey, String endpoint, LlmChatRequest request) {
    String canonicalEndpoint = byokEndpointValidator.validateAnthropic(endpoint);
    String plaintextApiKey = new String(decryptedKey, StandardCharsets.UTF_8);
    try {
      AnthropicChatOptions.Builder chatOptionsBuilder = AnthropicChatOptions.builder();
      chatOptionsBuilder.apiKey(plaintextApiKey);
      chatOptionsBuilder.baseUrl(canonicalEndpoint);
      chatOptionsBuilder.model(Model.of(request.model()));
      chatOptionsBuilder.temperature(request.temperature());
      chatOptionsBuilder.maxTokens(MAX_TOKENS);
      chatOptionsBuilder.internalToolExecutionEnabled(false);
      if (request.toolChoiceRequired()) {
        chatOptionsBuilder.toolChoice(ToolChoice.ofAny(ToolChoiceAny.builder().build()));
      }

      ChatResponse chatResponse =
          parentAnthropicChatClient
              .prompt()
              .system(request.systemPrompt())
              .user(request.userMessage())
              .toolCallbacks(translateTools(request.tools()))
              .options(chatOptionsBuilder)
              .call()
              .chatResponse();
      if (chatResponse == null) {
        throw new IllegalStateException("No chat response returned");
      }
      return toLlmChatResult(chatResponse);
    } finally {
      plaintextApiKey = null;
    }
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
