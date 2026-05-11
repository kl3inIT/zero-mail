package com.zeromail.core.llm.gateway.springai;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.zeromail.core.config.ZeroMailCoreProperties;
import com.zeromail.core.config.ZeroMailCoreProperties.ZeroMailLlmByokProperties;
import com.zeromail.core.llm.byok.ByokEndpointValidator;
import com.zeromail.core.llm.exception.SafetyViolationException;
import com.zeromail.core.llm.service.ByokLlmModelClient;
import com.zeromail.core.llm.usecases.LlmChatRequest;
import com.zeromail.core.llm.usecases.LlmChatResult;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Component;

@Component
public class GoogleGenAiByokModelClient implements ByokLlmModelClient {

    private final ByokEndpointValidator byokEndpointValidator;
    private final ZeroMailLlmByokProperties byokProperties;
    private final SpringAiByokChatSupport chatSupport = new SpringAiByokChatSupport();

    public GoogleGenAiByokModelClient(
            ByokEndpointValidator byokEndpointValidator,
            ZeroMailCoreProperties zeroMailCoreProperties) {
        this.byokEndpointValidator = byokEndpointValidator;
        this.byokProperties = zeroMailCoreProperties.llm().byok();
    }

    @Override
    public LlmChatResult call(byte[] decryptedKey, String endpoint, LlmChatRequest request) {
        byokEndpointValidator.validateGoogleGenAi(endpoint);
        if (request.toolChoiceRequired()) {
            throw new SafetyViolationException();
        }
        String plaintextApiKey = new String(decryptedKey, StandardCharsets.UTF_8);
        GoogleGenAiChatModel derivedModel = null;
        ChatClient derivedChatClient = null;
        try {
            Client genAiClient =
                    Client.builder()
                            .apiKey(plaintextApiKey)
                            .httpOptions(
                                    HttpOptions.builder()
                                            .timeout(timeoutMillis(byokProperties.readTimeout()))
                                            .build())
                            .build();
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

    private static int timeoutMillis(Duration timeout) {
        long timeoutMillis = timeout.toMillis();
        if (timeoutMillis > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.toIntExact(Math.max(1L, timeoutMillis));
    }
}
