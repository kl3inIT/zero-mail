package com.zeromail.core.llm.gateway.springai;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import com.zeromail.core.llm.application.LlmChatResult;
import com.zeromail.core.llm.application.LlmTool;
import com.zeromail.core.llm.application.LlmUsage;
import com.zeromail.core.llm.application.RawToolCall;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

final class SpringAiByokChatSupport {

  private final ObjectMapper objectMapper = new ObjectMapper();

  List<ToolCallback> translateTools(List<LlmTool> tools) {
    return tools.stream().map(this::toToolCallback).toList();
  }

  LlmChatResult toLlmChatResult(ChatResponse chatResponse) {
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

  private int tokenCount(Integer tokenCount) {
    return tokenCount == null ? 0 : tokenCount;
  }
}
