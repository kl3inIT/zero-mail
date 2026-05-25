package com.zeromail.core.llm.gateway.springai;

import com.zeromail.core.config.ZeroMailCoreProperties;
import com.zeromail.core.config.ZeroMailCoreProperties.ZeroMailLlmProperties;
import com.zeromail.core.llm.routing.PlatformLlmRouteCredentials;
import com.zeromail.core.llm.usecases.LlmChatRequest;
import com.zeromail.core.llm.usecases.LlmChatResult;
import com.zeromail.core.llm.usecases.LlmCredentialSource;
import com.zeromail.core.llm.usecases.LlmModelClient;
import com.zeromail.core.llm.usecases.LlmProviderChatExecutor;
import com.zeromail.core.llm.usecases.LlmProviderCredential;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
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
@Profile("!e2e-stub")
public class SpringAiLlmModelClient implements LlmModelClient {

    private final ChatClient platformChatClient;
    private final ZeroMailLlmProperties llmProperties;
    private final LlmProviderChatExecutor providerChatExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public SpringAiLlmModelClient(
            @Qualifier("platformChatClient") ChatClient platformChatClient,
            ZeroMailCoreProperties zeroMailCoreProperties,
            LlmProviderChatExecutor providerChatExecutor) {
        this(platformChatClient, zeroMailCoreProperties.llm().platform(), providerChatExecutor);
    }

    SpringAiLlmModelClient(ChatClient platformChatClient) {
        this(
                platformChatClient,
                new ZeroMailLlmProperties(
                        null, null, "test-platform-key", null, null, null, null, null, null),
                null);
    }

    private SpringAiLlmModelClient(
            ChatClient platformChatClient,
            ZeroMailLlmProperties llmProperties,
            LlmProviderChatExecutor providerChatExecutor) {
        this.platformChatClient = platformChatClient;
        this.llmProperties = llmProperties;
        this.providerChatExecutor = providerChatExecutor;
    }

    @Override
    public LlmChatResult call(LlmChatRequest request) {
        return callWithClient(platformChatClient, request);
    }

    @Override
    public LlmChatResult call(
            LlmChatRequest request, PlatformLlmRouteCredentials routeCredentials) {
        if (providerChatExecutor == null) {
            throw new IllegalStateException("Provider chat executor is unavailable");
        }
        return providerChatExecutor.call(
                new LlmProviderCredential(
                        routeCredentials.providerId(),
                        routeCredentials.keyFormat(),
                        routeCredentials.baseUrl(),
                        routeCredentials.plaintextKey(),
                        LlmCredentialSource.PLATFORM),
                request);
    }

    private LlmChatResult callWithClient(ChatClient chatClient, LlmChatRequest request) {
        ChatResponse chatResponse =
                chatClient
                        .prompt()
                        .system(request.systemPrompt())
                        .user(request.userMessage())
                        .tools(toolSpec -> toolSpec.callbacks(translateTools(request.tools())))
                        .advisors(SpringAiRawToolCallSupport::preserveRawToolCalls)
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
                        .timeout(llmProperties.readTimeout())
                        .internalToolExecutionEnabled(false);
        if (request.toolChoiceRequired()) {
            chatOptionsBuilder.toolChoice(OpenAiToolChoiceOptions.required());
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
