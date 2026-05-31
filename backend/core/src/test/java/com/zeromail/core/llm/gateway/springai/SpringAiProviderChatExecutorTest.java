package com.zeromail.core.llm.gateway.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.llm.usecases.LlmChatRequest;
import com.zeromail.core.llm.usecases.LlmChatResult;
import com.zeromail.core.llm.usecases.LlmCredentialSource;
import com.zeromail.core.llm.usecases.LlmProviderCredential;
import com.zeromail.core.llm.usecases.LlmTool;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;

class SpringAiProviderChatExecutorTest {

    @Test
    void preserves_raw_tool_calls_for_dynamic_provider_credentials() {
        SpringAiProviderChatClientFactory chatClientFactory =
                mock(SpringAiProviderChatClientFactory.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec chatClientRequestSpecification =
                mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpecification =
                mock(ChatClient.CallResponseSpec.class);
        LlmProviderCredential credential =
                new LlmProviderCredential(
                        "CUSTOM_OPENAI",
                        SpringAiProviderChatClientFactory.OPENAI_FORMAT,
                        "https://example.test/v1",
                        "test-key".getBytes(StandardCharsets.UTF_8),
                        LlmCredentialSource.PLATFORM);
        LlmChatRequest request =
                new LlmChatRequest(
                        "system",
                        "user",
                        List.of(
                                new LlmTool(
                                        "extract_rule",
                                        "Extract a rule",
                                        Map.of("type", "object", "properties", Map.of()))),
                        "cx/gpt-5.5",
                        0.0,
                        128,
                        true);
        when(chatClientFactory.create(
                        any(LlmProviderCredential.class),
                        anyString(),
                        ArgumentMatchers.anyDouble(),
                        any(),
                        ArgumentMatchers.anyBoolean()))
                .thenReturn(chatClient);
        doReturn(OpenAiChatOptions.builder())
                .when(chatClientFactory)
                .options(
                        any(LlmProviderCredential.class),
                        anyString(),
                        ArgumentMatchers.anyDouble(),
                        any(),
                        ArgumentMatchers.anyBoolean());
        when(chatClient.prompt()).thenReturn(chatClientRequestSpecification);
        when(chatClientRequestSpecification.system(anyString()))
                .thenReturn(chatClientRequestSpecification);
        when(chatClientRequestSpecification.user(anyString()))
                .thenReturn(chatClientRequestSpecification);
        when(chatClientRequestSpecification.tools(
                        ArgumentMatchers.<Consumer<ChatClient.ToolSpec>>any()))
                .thenReturn(chatClientRequestSpecification);
        when(chatClientRequestSpecification.advisors(
                        ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()))
                .thenReturn(chatClientRequestSpecification);
        when(chatClientRequestSpecification.options(any(ChatOptions.Builder.class)))
                .thenReturn(chatClientRequestSpecification);
        when(chatClientRequestSpecification.call()).thenReturn(callResponseSpecification);
        when(callResponseSpecification.chatResponse())
                .thenReturn(
                        chatResponseWithToolCalls(
                                List.of(
                                        new AssistantMessage.ToolCall(
                                                "call-1",
                                                "function",
                                                "extract_rule",
                                                "{\"labelName\":\"Receipts\"}"))));
        SpringAiProviderChatExecutor executor = new SpringAiProviderChatExecutor(chatClientFactory);

        LlmChatResult result = executor.call(credential, request);

        assertThat(result.toolCalls())
                .singleElement()
                .satisfies(
                        toolCall -> {
                            assertThat(toolCall.functionName()).isEqualTo("extract_rule");
                            assertThat(toolCall.argsJson()).contains("Receipts");
                        });
        assertRawToolCallAdvisorAutoRegistrationDisabled(chatClientRequestSpecification);
    }

    @Test
    void omits_tool_callbacks_for_text_generation_requests() {
        SpringAiProviderChatClientFactory chatClientFactory =
                mock(SpringAiProviderChatClientFactory.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec chatClientRequestSpecification =
                mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpecification =
                mock(ChatClient.CallResponseSpec.class);
        LlmProviderCredential credential =
                new LlmProviderCredential(
                        "CUSTOM_OPENAI",
                        SpringAiProviderChatClientFactory.OPENAI_FORMAT,
                        "https://example.test/v1",
                        "test-key".getBytes(StandardCharsets.UTF_8),
                        LlmCredentialSource.BYOK);
        LlmChatRequest request =
                new LlmChatRequest(
                        "system",
                        "text-generation-user-message",
                        List.of(),
                        "cx/gpt-5.5",
                        0.2,
                        128,
                        false);
        when(chatClientFactory.create(
                        any(LlmProviderCredential.class),
                        anyString(),
                        ArgumentMatchers.anyDouble(),
                        any(),
                        ArgumentMatchers.anyBoolean()))
                .thenReturn(chatClient);
        doReturn(OpenAiChatOptions.builder())
                .when(chatClientFactory)
                .options(
                        any(LlmProviderCredential.class),
                        anyString(),
                        ArgumentMatchers.anyDouble(),
                        any(),
                        ArgumentMatchers.anyBoolean());
        when(chatClient.prompt()).thenReturn(chatClientRequestSpecification);
        when(chatClientRequestSpecification.system(anyString()))
                .thenReturn(chatClientRequestSpecification);
        when(chatClientRequestSpecification.user(anyString()))
                .thenReturn(chatClientRequestSpecification);
        when(chatClientRequestSpecification.advisors(
                        ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()))
                .thenReturn(chatClientRequestSpecification);
        when(chatClientRequestSpecification.options(any(ChatOptions.Builder.class)))
                .thenReturn(chatClientRequestSpecification);
        when(chatClientRequestSpecification.call()).thenReturn(callResponseSpecification);
        when(callResponseSpecification.chatResponse())
                .thenReturn(chatResponseWithToolCalls(List.of()));
        SpringAiProviderChatExecutor executor = new SpringAiProviderChatExecutor(chatClientFactory);

        executor.call(credential, request);

        verify(chatClientRequestSpecification, never())
                .tools(ArgumentMatchers.<Consumer<ChatClient.ToolSpec>>any());
    }

    private void assertRawToolCallAdvisorAutoRegistrationDisabled(
            ChatClient.ChatClientRequestSpec chatClientRequestSpecification) {
        ArgumentCaptor<Consumer<ChatClient.AdvisorSpec>> advisorSpecConsumerCaptor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(chatClientRequestSpecification).advisors(advisorSpecConsumerCaptor.capture());
        ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class);
        when(advisorSpec.param(anyString(), any())).thenReturn(advisorSpec);

        advisorSpecConsumerCaptor.getValue().accept(advisorSpec);

        verify(advisorSpec)
                .param(ChatClientAttributes.TOOL_CALL_ADVISOR_AUTO_REGISTER.getKey(), false);
    }

    private ChatResponse chatResponseWithToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        AssistantMessage assistantMessage = AssistantMessage.builder().toolCalls(toolCalls).build();
        return ChatResponse.builder()
                .generations(List.of(new Generation(assistantMessage)))
                .build();
    }
}
