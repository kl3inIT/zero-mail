package com.zeromail.core.llm.gateway.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.llm.config.LlmProperties;
import com.zeromail.core.llm.config.LlmProperties.PlatformProperties;
import com.zeromail.core.llm.usecases.LlmChatRequest;
import com.zeromail.core.llm.usecases.LlmChatResult;
import com.zeromail.core.llm.usecases.LlmCredentialSource;
import com.zeromail.core.llm.usecases.LlmProviderCredential;
import com.zeromail.core.llm.usecases.LlmTool;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.web.client.RestClient;

class SpringAiProviderChatClientFactoryTest {

    @Test
    void native_openai_uses_max_completion_tokens_not_max_tokens() {
        // Native OpenAI reasoning models (gpt-5.x) reject `max_tokens` with a 400
        // unsupported_parameter error and require `max_completion_tokens`. The adapter must emit
        // the
        // new field for the native OpenAI provider so chat does not break on those models.
        SpringAiProviderChatClientFactory factory =
                new SpringAiProviderChatClientFactory(llmProperties(), RestClient.builder());

        OpenAiChatOptions options =
                (OpenAiChatOptions)
                        factory.options(
                                        "OPENAI",
                                        SpringAiProviderChatClientFactory.OPENAI_FORMAT,
                                        LlmCredentialSource.PLATFORM,
                                        "gpt-5.4-mini",
                                        0.2,
                                        2048,
                                        false)
                                .build();

        assertThat(options.getMaxCompletionTokens()).isEqualTo(2048);
        assertThat(options.getMaxTokens()).isNull();
        // Reasoning models also reject a non-default temperature, so the native OpenAI provider
        // must
        // omit it entirely.
        assertThat(options.getTemperature()).isNull();
    }

    @Test
    void openai_compatible_gateway_keeps_legacy_max_tokens() {
        // OpenAI-compatible gateways (9router, OpenRouter) still expect the legacy `max_tokens`
        // field, so the provider-specific switch must NOT rewrite it for them.
        SpringAiProviderChatClientFactory factory =
                new SpringAiProviderChatClientFactory(llmProperties(), RestClient.builder());

        OpenAiChatOptions options =
                (OpenAiChatOptions)
                        factory.options(
                                        "ROUTER_9R",
                                        SpringAiProviderChatClientFactory.OPENAI_FORMAT,
                                        LlmCredentialSource.PLATFORM,
                                        "cx/gpt-5.5",
                                        0.2,
                                        2048,
                                        false)
                                .build();

        assertThat(options.getMaxTokens()).isEqualTo(2048);
        assertThat(options.getMaxCompletionTokens()).isNull();
        // Gateways honor an explicit temperature, so it must be preserved for them.
        assertThat(options.getTemperature()).isEqualTo(0.2);
    }

    @Test
    void google_format_required_tool_choice_is_route_configuration_failure_not_safety() {
        SpringAiProviderChatClientFactory factory =
                new SpringAiProviderChatClientFactory(llmProperties(), RestClient.builder());

        assertThatThrownBy(
                        () ->
                                factory.options(
                                        "GOOGLE",
                                        SpringAiProviderChatClientFactory.GOOGLE_FORMAT,
                                        LlmCredentialSource.PLATFORM,
                                        "gemini-2.5-flash",
                                        0.0,
                                        512,
                                        true))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("forced tool choice");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_9ROUTER_TOOL_PROBE", matches = "true")
    void nine_router_openai_compatible_route_returns_tool_calls_through_spring_ai() {
        String apiKey = System.getenv("NINE_ROUTER_API_KEY");
        String baseUrl =
                System.getenv()
                        .getOrDefault("NINE_ROUTER_BASE_URL", "https://9router.zeromail.vn/v1");
        String model = System.getenv().getOrDefault("NINE_ROUTER_MODEL", "cx/gpt-5.5");
        SpringAiProviderChatClientFactory factory =
                new SpringAiProviderChatClientFactory(llmProperties(), RestClient.builder());
        SpringAiProviderChatExecutor executor = new SpringAiProviderChatExecutor(factory);

        LlmChatResult result =
                executor.call(
                        new LlmProviderCredential(
                                "ROUTER_9R",
                                SpringAiProviderChatClientFactory.OPENAI_FORMAT,
                                baseUrl,
                                apiKey.getBytes(StandardCharsets.UTF_8),
                                LlmCredentialSource.PLATFORM),
                        new LlmChatRequest(
                                "You must call the provided tool.",
                                "Call extract_rule with labelName Receipts and matcherIntent invoice.",
                                List.of(extractRuleTool()),
                                model,
                                0.0,
                                200,
                                true));

        org.assertj.core.api.Assertions.assertThat(result.toolCalls())
                .singleElement()
                .satisfies(
                        toolCall -> {
                            org.assertj.core.api.Assertions.assertThat(toolCall.functionName())
                                    .isEqualTo("extract_rule");
                            org.assertj.core.api.Assertions.assertThat(toolCall.argsJson())
                                    .contains("Receipts", "invoice");
                        });
    }

    private static LlmTool extractRuleTool() {
        return new LlmTool(
                "extract_rule",
                "Extract a simple email rule.",
                Map.of(
                        "type",
                        "object",
                        "additionalProperties",
                        false,
                        "properties",
                        Map.of(
                                "labelName",
                                Map.of("type", "string"),
                                "matcherIntent",
                                Map.of("type", "string")),
                        "required",
                        List.of("labelName", "matcherIntent")));
    }

    private static LlmProperties llmProperties() {
        return new LlmProperties(
                new PlatformProperties(
                        "openai",
                        "https://openrouter.ai/api/v1",
                        "test-platform-key",
                        "openai/gpt-5.4-nano",
                        "openai/gpt-5.4-nano",
                        "openai/gpt-5.4-nano",
                        "openai/gpt-5.4-nano",
                        null,
                        null),
                null);
    }
}
