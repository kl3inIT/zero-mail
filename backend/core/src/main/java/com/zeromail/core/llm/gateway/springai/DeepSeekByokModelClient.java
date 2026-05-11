package com.zeromail.core.llm.gateway.springai;

import com.zeromail.core.llm.application.LlmChatRequest;
import com.zeromail.core.llm.application.LlmChatResult;
import com.zeromail.core.llm.byok.ByokEndpointValidator;
import com.zeromail.core.llm.service.ByokLlmModelClient;
import java.nio.charset.StandardCharsets;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DeepSeekByokModelClient implements ByokLlmModelClient {

    private final ByokEndpointValidator byokEndpointValidator;
    private final RestClient.Builder restClientBuilder;
    private final SpringAiByokChatSupport chatSupport = new SpringAiByokChatSupport();

    public DeepSeekByokModelClient(
            ByokEndpointValidator byokEndpointValidator, RestClient.Builder restClientBuilder) {
        this.byokEndpointValidator = byokEndpointValidator;
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public LlmChatResult call(byte[] decryptedKey, String endpoint, LlmChatRequest request) {
        String canonicalEndpoint = byokEndpointValidator.validateDeepSeek(endpoint);
        String plaintextApiKey = new String(decryptedKey, StandardCharsets.UTF_8);
        DeepSeekChatModel derivedModel = null;
        ChatClient derivedChatClient = null;
        try {
            DeepSeekApi deepSeekApi =
                    DeepSeekApi.builder()
                            .apiKey(plaintextApiKey)
                            .baseUrl(canonicalEndpoint)
                            .restClientBuilder(restClientBuilder.clone())
                            .build();
            derivedModel =
                    DeepSeekChatModel.builder()
                            .deepSeekApi(deepSeekApi)
                            .defaultOptions(
                                    DeepSeekChatOptions.builder()
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

    private DeepSeekChatOptions.Builder chatOptions(LlmChatRequest request) {
        DeepSeekChatOptions.Builder chatOptionsBuilder =
                DeepSeekChatOptions.builder()
                        .model(request.model())
                        .temperature(request.temperature())
                        .internalToolExecutionEnabled(false);
        if (request.toolChoiceRequired()) {
            chatOptionsBuilder.toolChoice("required");
        }
        return chatOptionsBuilder;
    }
}
