package com.zeromail.core.llm.gateway.springai;

import com.zeromail.core.llm.config.LlmProperties;
import com.zeromail.core.llm.config.LlmProperties.PlatformProperties;
import com.zeromail.core.llm.routing.PlatformLlmRouteCredentials;
import com.zeromail.core.llm.usecases.LlmChatRequest;
import com.zeromail.core.llm.usecases.LlmChatResult;
import com.zeromail.core.llm.usecases.LlmCredentialSource;
import com.zeromail.core.llm.usecases.LlmModelClient;
import com.zeromail.core.llm.usecases.LlmProviderChatExecutor;
import com.zeromail.core.llm.usecases.LlmProviderCredential;
import com.zeromail.core.llm.usecases.LlmTool;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

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
    private final PlatformProperties llmProperties;
    private final LlmProviderChatExecutor providerChatExecutor;
    private final SpringAiLlmChatSupport chatSupport = new SpringAiLlmChatSupport();

    @Autowired
    public SpringAiLlmModelClient(
            @Qualifier("platformChatClient") ChatClient platformChatClient,
            LlmProperties llmConfiguration,
            LlmProviderChatExecutor providerChatExecutor) {
        this(platformChatClient, llmConfiguration.platform(), providerChatExecutor);
    }

    SpringAiLlmModelClient(ChatClient platformChatClient) {
        this(
                platformChatClient,
                new PlatformProperties(
                        null, null, "test-platform-key", null, null, null, null, null, null),
                null);
    }

    private SpringAiLlmModelClient(
            ChatClient platformChatClient,
            PlatformProperties llmProperties,
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
        ChatClient.ChatClientRequestSpec requestSpecification =
                chatClient.prompt().system(request.systemPrompt()).user(request.userMessage());
        if (!request.tools().isEmpty()) {
            requestSpecification =
                    requestSpecification.tools(
                            toolSpec -> toolSpec.callbacks(translateTools(request.tools())));
        }
        ChatResponse chatResponse =
                requestSpecification
                        .advisors(SpringAiRawToolCallSupport::preserveRawToolCalls)
                        .options(chatOptions(request))
                        .call()
                        .chatResponse();
        if (chatResponse == null) {
            throw new IllegalStateException("No chat response returned");
        }
        return chatSupport.toLlmChatResult(chatResponse);
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
        return chatSupport.translateTools(tools);
    }
}
