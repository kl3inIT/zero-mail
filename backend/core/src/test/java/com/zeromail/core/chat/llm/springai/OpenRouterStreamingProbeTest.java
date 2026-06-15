package com.zeromail.core.chat.llm.springai;

import static org.assertj.core.api.Assertions.assertThat;

import com.openai.errors.OpenAIServiceException;
import com.zeromail.core.chat.usecases.ChatToolCatalog;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

@Tag("manual-openrouter")
@EnabledIfEnvironmentVariable(named = "RUN_OPENROUTER_STREAM_PROBE", matches = "true")
class OpenRouterStreamingProbeTest {

    private static final String OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1";

    @Test
    void spring_ai_openrouter_streams_without_tools() {
        List<ChatResponse> responses =
                stream("openai/gpt-5.4-nano", OpenAiChatOptions.builder().build());

        assertThat(responses).isNotEmpty();
    }

    @Test
    void spring_ai_openrouter_streams_with_zero_mail_tools() {
        ToolCallbackTranslator toolCallbackTranslator = new ToolCallbackTranslator();
        ChatToolCatalog chatToolCatalog = new ChatToolCatalog();

        List<ChatResponse> responses =
                stream(
                        "openai/gpt-5.4-nano",
                        OpenAiChatOptions.builder()
                                .toolCallbacks(toolCallbackTranslator.translate(chatToolCatalog))
                                .build());

        assertThat(responses).isNotEmpty();
    }

    @Test
    void each_zero_mail_tool_schema_streams_individually() {
        List<String> failingToolNames = new ArrayList<>();
        ChatToolCatalog chatToolCatalog = new ChatToolCatalog();

        for (ChatToolCatalog.ToolDefinition toolDefinition : chatToolCatalog.toolDefinitions()) {
            try {
                stream(
                        "openai/gpt-5.4-nano",
                        OpenAiChatOptions.builder()
                                .toolCallbacks(List.of(toolCallback(toolDefinition)))
                                .build());
            } catch (RuntimeException runtimeException) {
                failingToolNames.add(
                        toolDefinition.name().id()
                                + " -> "
                                + rootCause(runtimeException).getClass().getSimpleName()
                                + " "
                                + serviceExceptionSummary(rootCause(runtimeException))
                                + " schema="
                                + JsonSchemaGenerator.generateForType(
                                        toolDefinition.argsRecordClass()));
            }
        }

        assertThat(failingToolNames).isEmpty();
    }

    private ToolCallback toolCallback(ChatToolCatalog.ToolDefinition toolDefinition) {
        return FunctionToolCallback.builder(
                        toolDefinition.name().id(), (Object ignoredInput) -> Map.of())
                .description(toolDefinition.description())
                .inputSchema(
                        ToolCallbackTranslator.inputSchemaFor(toolDefinition.argsRecordClass()))
                .inputType(toolDefinition.argsRecordClass())
                .build();
    }

    private List<ChatResponse> stream(String modelId, OpenAiChatOptions additionalOptions) {
        String apiKey = System.getenv("ZEROMAIL_LLM_PLATFORM_API_KEY");
        assertThat(apiKey).as("ZEROMAIL_LLM_PLATFORM_API_KEY").isNotBlank();

        OpenAiChatModel chatModel =
                OpenAiChatModel.builder()
                        .options(
                                OpenAiChatOptions.builder()
                                        .baseUrl(OPENROUTER_BASE_URL)
                                        .apiKey(apiKey)
                                        .model(modelId)
                                        .temperature(0.2)
                                        .maxTokens(32)
                                        .streamUsage(false)
                                        .build())
                        .build();

        Prompt prompt =
                new Prompt(
                        new UserMessage("Return exactly: ok"),
                        OpenAiChatOptions.builder()
                                .model(modelId)
                                .temperature(0.2)
                                .maxTokens(32)
                                .streamUsage(false)
                                .toolCallbacks(additionalOptions.getToolCallbacks())
                                .build());

        return chatModel.stream(prompt)
                .timeout(Duration.ofSeconds(30))
                .collectList()
                .block(Duration.ofSeconds(35));
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable currentThrowable = throwable;
        while (currentThrowable.getCause() != null) {
            currentThrowable = currentThrowable.getCause();
        }
        return currentThrowable;
    }

    private static String serviceExceptionSummary(Throwable throwable) {
        if (!(throwable instanceof OpenAIServiceException openAIServiceException)) {
            return "";
        }
        try {
            return "statusCode="
                    + openAIServiceException.statusCode()
                    + " code="
                    + openAIServiceException.code().orElse("-")
                    + " param="
                    + openAIServiceException.param().orElse("-")
                    + " type="
                    + openAIServiceException.type().orElse("-")
                    + " body="
                    + openAIServiceException.body();
        } catch (RuntimeException summaryException) {
            return "statusCode="
                    + openAIServiceException.statusCode()
                    + " summaryError="
                    + summaryException.getClass().getSimpleName();
        }
    }
}
