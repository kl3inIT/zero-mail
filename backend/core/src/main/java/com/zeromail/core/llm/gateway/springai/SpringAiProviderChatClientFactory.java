package com.zeromail.core.llm.gateway.springai;

import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.ToolChoice;
import com.anthropic.models.messages.ToolChoiceAny;
import com.zeromail.core.llm.config.LlmProperties;
import com.zeromail.core.llm.usecases.LlmCredentialSource;
import com.zeromail.core.llm.usecases.LlmProviderCredential;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SpringAiProviderChatClientFactory {

    static final String OPENAI_FORMAT = "OPENAI_FORMAT";
    static final String ANTHROPIC_FORMAT = "ANTHROPIC_FORMAT";
    static final String GOOGLE_FORMAT = "GOOGLE_FORMAT";
    private static final String DEEPSEEK_PROVIDER_ID = "DEEPSEEK";
    private static final int ANTHROPIC_DEFAULT_MAX_TOKENS = 512;

    private final LlmProperties llmProperties;
    private final RestClient.Builder restClientBuilder;

    public SpringAiProviderChatClientFactory(
            LlmProperties llmProperties, RestClient.Builder restClientBuilder) {
        this.llmProperties = llmProperties;
        this.restClientBuilder = restClientBuilder;
    }

    public ChatClient create(
            LlmProviderCredential credential,
            String model,
            double temperature,
            Integer maxTokens,
            boolean toolChoiceRequired) {
        byte[] localKeyCopy = credential.plaintextKey();
        String plaintextApiKey = new String(localKeyCopy, StandardCharsets.UTF_8);
        try {
            return switch (credential.keyFormat()) {
                case OPENAI_FORMAT -> {
                    if (DEEPSEEK_PROVIDER_ID.equals(credential.providerId())) {
                        yield ChatClient.create(
                                deepSeekModel(credential, model, temperature, plaintextApiKey));
                    }
                    yield ChatClient.create(
                            openAiCompatibleModel(
                                    credential, model, temperature, maxTokens, plaintextApiKey));
                }
                case ANTHROPIC_FORMAT ->
                        ChatClient.create(
                                anthropicModel(
                                        credential,
                                        model,
                                        temperature,
                                        maxTokens,
                                        toolChoiceRequired,
                                        plaintextApiKey));
                case GOOGLE_FORMAT ->
                        ChatClient.create(
                                googleGenAiModel(credential, model, temperature, plaintextApiKey));
                default ->
                        throw new IllegalStateException(
                                "Unsupported LLM key format: " + credential.keyFormat());
            };
        } finally {
            Arrays.fill(localKeyCopy, (byte) 0);
        }
    }

    public ChatOptions.Builder<?> options(
            LlmProviderCredential credential,
            String model,
            double temperature,
            Integer maxTokens,
            boolean toolChoiceRequired) {
        return options(
                credential.providerId(),
                credential.keyFormat(),
                credential.source(),
                model,
                temperature,
                maxTokens,
                toolChoiceRequired);
    }

    public ChatOptions.Builder<?> options(
            String providerId,
            String keyFormat,
            LlmCredentialSource source,
            String model,
            double temperature,
            Integer maxTokens,
            boolean toolChoiceRequired) {
        return switch (keyFormat) {
            case OPENAI_FORMAT -> {
                if (DEEPSEEK_PROVIDER_ID.equals(providerId)) {
                    yield deepSeekOptions(model, temperature, toolChoiceRequired);
                }
                yield openAiOptions(model, temperature, maxTokens, toolChoiceRequired, source);
            }
            case ANTHROPIC_FORMAT ->
                    anthropicOptions(model, temperature, maxTokens, toolChoiceRequired, source);
            case GOOGLE_FORMAT -> googleOptions(model, temperature, toolChoiceRequired);
            default -> throw new IllegalStateException("Unsupported LLM key format: " + keyFormat);
        };
    }

    private OpenAiChatModel openAiCompatibleModel(
            LlmProviderCredential credential,
            String model,
            double temperature,
            Integer maxTokens,
            String plaintextApiKey) {
        return OpenAiChatModel.builder()
                .options(
                        openAiOptions(model, temperature, maxTokens, false, credential.source())
                                .apiKey(plaintextApiKey)
                                .baseUrl(credential.baseUrl())
                                .build())
                .build();
    }

    private OpenAiChatOptions.Builder openAiOptions(
            String model,
            double temperature,
            Integer maxTokens,
            boolean toolChoiceRequired,
            LlmCredentialSource source) {
        OpenAiChatOptions.Builder chatOptionsBuilder =
                OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .timeout(timeoutFor(source))
                        .internalToolExecutionEnabled(false);
        if (toolChoiceRequired) {
            chatOptionsBuilder.toolChoice(OpenAiToolChoiceOptions.required());
        }
        if (maxTokens != null) {
            chatOptionsBuilder.maxTokens(maxTokens);
        }
        return chatOptionsBuilder;
    }

    private AnthropicChatModel anthropicModel(
            LlmProviderCredential credential,
            String model,
            double temperature,
            Integer maxTokens,
            boolean toolChoiceRequired,
            String plaintextApiKey) {
        return AnthropicChatModel.builder()
                .options(
                        anthropicOptions(
                                        model,
                                        temperature,
                                        maxTokens,
                                        toolChoiceRequired,
                                        credential.source())
                                .apiKey(plaintextApiKey)
                                .baseUrl(credential.baseUrl())
                                .build())
                .build();
    }

    private AnthropicChatOptions.Builder anthropicOptions(
            String model,
            double temperature,
            Integer maxTokens,
            boolean toolChoiceRequired,
            LlmCredentialSource source) {
        AnthropicChatOptions.Builder chatOptionsBuilder = AnthropicChatOptions.builder();
        chatOptionsBuilder.model(Model.of(model));
        chatOptionsBuilder.temperature(temperature);
        chatOptionsBuilder.maxTokens(maxTokens == null ? ANTHROPIC_DEFAULT_MAX_TOKENS : maxTokens);
        chatOptionsBuilder.timeout(timeoutFor(source));
        chatOptionsBuilder.internalToolExecutionEnabled(false);
        if (toolChoiceRequired) {
            chatOptionsBuilder.toolChoice(ToolChoice.ofAny(ToolChoiceAny.builder().build()));
        }
        return chatOptionsBuilder;
    }

    private GoogleGenAiChatModel googleGenAiModel(
            LlmProviderCredential credential,
            String model,
            double temperature,
            String plaintextApiKey) {
        com.google.genai.Client genAiClient =
                com.google.genai.Client.builder()
                        .apiKey(plaintextApiKey)
                        .httpOptions(
                                com.google.genai.types.HttpOptions.builder()
                                        .timeout(timeoutMillis(timeoutFor(credential.source())))
                                        .build())
                        .build();
        return GoogleGenAiChatModel.builder()
                .genAiClient(genAiClient)
                .defaultOptions(googleOptions(model, temperature, false).build())
                .build();
    }

    private GoogleGenAiChatOptions.Builder googleOptions(
            String model, double temperature, boolean toolChoiceRequired) {
        if (toolChoiceRequired) {
            throw new UnsupportedOperationException(
                    "Google GenAI forced tool choice is not supported by this adapter");
        }
        return GoogleGenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .internalToolExecutionEnabled(false);
    }

    private DeepSeekChatModel deepSeekModel(
            LlmProviderCredential credential,
            String model,
            double temperature,
            String plaintextApiKey) {
        DeepSeekApi deepSeekApi =
                DeepSeekApi.builder()
                        .apiKey(plaintextApiKey)
                        .baseUrl(credential.baseUrl())
                        .restClientBuilder(restClientBuilder.clone())
                        .build();
        return DeepSeekChatModel.builder()
                .deepSeekApi(deepSeekApi)
                .defaultOptions(deepSeekOptions(model, temperature, false).build())
                .build();
    }

    private DeepSeekChatOptions.Builder deepSeekOptions(
            String model, double temperature, boolean toolChoiceRequired) {
        DeepSeekChatOptions.Builder chatOptionsBuilder =
                DeepSeekChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .internalToolExecutionEnabled(false);
        if (toolChoiceRequired) {
            chatOptionsBuilder.toolChoice("required");
        }
        return chatOptionsBuilder;
    }

    private Duration timeoutFor(LlmCredentialSource source) {
        return source == LlmCredentialSource.PLATFORM
                ? llmProperties.platform().readTimeout()
                : llmProperties.byok().readTimeout();
    }

    private static int timeoutMillis(Duration timeout) {
        long timeoutMillis = timeout.toMillis();
        if (timeoutMillis > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.toIntExact(Math.max(1L, timeoutMillis));
    }
}
