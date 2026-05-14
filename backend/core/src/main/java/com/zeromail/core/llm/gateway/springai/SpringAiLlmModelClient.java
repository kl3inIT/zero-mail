package com.zeromail.core.llm.gateway.springai;

import com.zeromail.core.llm.usecases.LlmChatRequest;
import com.zeromail.core.llm.usecases.LlmChatResult;
import com.zeromail.core.llm.usecases.LlmModelClient;
import com.zeromail.core.llm.usecases.LlmTool;
import com.zeromail.core.llm.usecases.LlmUsage;
import com.zeromail.core.llm.usecases.RawToolCall;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring AI adapter for the platform-path model client.
 *
 * <p>This class is the Spring AI boundary. Service-layer gateway code depends only on project-local
 * records and the {@link LlmModelClient} interface.
 */
@Component
@Primary
public class SpringAiLlmModelClient implements LlmModelClient {

    private final ChatClient platformChatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpringAiLlmModelClient(@Qualifier("platformChatClient") ChatClient platformChatClient) {
        this.platformChatClient = platformChatClient;
    }

    @Override
    public LlmChatResult call(LlmChatRequest request) {
        ChatResponse chatResponse =
                platformChatClient
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
        if (request.maxTokens() != null) {
            chatOptionsBuilder.maxTokens(request.maxTokens());
        }
        return chatOptionsBuilder;
    }

    private List<ToolCallback> translateTools(List<LlmTool> tools) {
        return tools.stream().map(this::toToolCallback).toList();
    }

    private ToolCallback toToolCallback(LlmTool tool) {
        return FunctionToolCallback.builder(
                        tool.name(), (Map<String, Object> toolInput) -> Map.of())
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
