package com.zeromail.core.llm.gateway.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openai.models.chat.completions.ChatCompletionToolChoiceOption;
import com.zeromail.core.llm.domain.AllowListedTools;
import com.zeromail.core.llm.usecases.LlmChatRequest;
import com.zeromail.core.llm.usecases.LlmChatResult;
import com.zeromail.core.llm.usecases.LlmTool;
import com.zeromail.core.llm.usecases.SystemPrompts;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatOptions;

class SpringAiLlmModelClientTest {

    @Test
    void applies_required_tool_choice_and_disables_internal_tool_execution() {
        ChatClient platformChatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec chatClientRequestSpecification =
                mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpecification =
                mock(ChatClient.CallResponseSpec.class);
        when(platformChatClient.prompt()).thenReturn(chatClientRequestSpecification);
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
        when(chatClientRequestSpecification.options(any(OpenAiChatOptions.Builder.class)))
                .thenReturn(chatClientRequestSpecification);
        when(chatClientRequestSpecification.call()).thenReturn(callResponseSpecification);
        when(callResponseSpecification.chatResponse())
                .thenReturn(
                        chatResponseWithToolCalls(
                                List.of(
                                        new AssistantMessage.ToolCall(
                                                "call-1",
                                                "function",
                                                "label",
                                                "{\"value\":\"Receipts\"}"))));
        SpringAiLlmModelClient modelClient = new SpringAiLlmModelClient(platformChatClient);
        LlmChatRequest request = request();

        LlmChatResult chatResult = modelClient.call(request);

        ArgumentCaptor<OpenAiChatOptions.Builder> openAiChatOptionsCaptor =
                ArgumentCaptor.forClass(OpenAiChatOptions.Builder.class);
        verify(chatClientRequestSpecification).system(SystemPrompts.TRIAGE_SYSTEM_PROMPT);
        verify(chatClientRequestSpecification).user("sanitized-user-message");
        assertRawToolCallAdvisorAutoRegistrationDisabled(chatClientRequestSpecification);
        verify(chatClientRequestSpecification).options(openAiChatOptionsCaptor.capture());
        OpenAiChatOptions capturedOptions = openAiChatOptionsCaptor.getValue().build();
        assertThat(capturedOptions.getToolChoice())
                .isInstanceOfSatisfying(
                        ChatCompletionToolChoiceOption.class,
                        toolChoice ->
                                assertThat(toolChoice.asAuto())
                                        .isEqualTo(ChatCompletionToolChoiceOption.Auto.REQUIRED));
        assertThat(capturedOptions.getInternalToolExecutionEnabled()).isFalse();
        assertThat(capturedOptions.getModel()).isEqualTo("openai/gpt-5.4-nano");
        assertThat(capturedOptions.getTemperature()).isEqualTo(0.0);
        assertThat(chatResult.toolCalls())
                .singleElement()
                .satisfies(
                        rawToolCall -> {
                            assertThat(rawToolCall.functionName()).isEqualTo("label");
                            assertThat(rawToolCall.argsJson())
                                    .isEqualTo("{\"value\":\"Receipts\"}");
                        });
    }

    @Test
    void returns_empty_tool_calls_when_upstream_response_has_none() {
        ChatClient platformChatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec chatClientRequestSpecification =
                mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpecification =
                mock(ChatClient.CallResponseSpec.class);
        when(platformChatClient.prompt()).thenReturn(chatClientRequestSpecification);
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
        when(chatClientRequestSpecification.options(any(OpenAiChatOptions.Builder.class)))
                .thenReturn(chatClientRequestSpecification);
        when(chatClientRequestSpecification.call()).thenReturn(callResponseSpecification);
        when(callResponseSpecification.chatResponse())
                .thenReturn(chatResponseWithToolCalls(List.of()));
        SpringAiLlmModelClient modelClient = new SpringAiLlmModelClient(platformChatClient);

        LlmChatResult chatResult = modelClient.call(request());

        assertThat(chatResult.toolCalls()).isEmpty();
    }

    @Test
    void omits_tool_callbacks_when_request_has_no_tools() {
        ChatClient platformChatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec chatClientRequestSpecification =
                mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpecification =
                mock(ChatClient.CallResponseSpec.class);
        when(platformChatClient.prompt()).thenReturn(chatClientRequestSpecification);
        when(chatClientRequestSpecification.system(anyString()))
                .thenReturn(chatClientRequestSpecification);
        when(chatClientRequestSpecification.user(anyString()))
                .thenReturn(chatClientRequestSpecification);
        when(chatClientRequestSpecification.advisors(
                        ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()))
                .thenReturn(chatClientRequestSpecification);
        when(chatClientRequestSpecification.options(any(OpenAiChatOptions.Builder.class)))
                .thenReturn(chatClientRequestSpecification);
        when(chatClientRequestSpecification.call()).thenReturn(callResponseSpecification);
        when(callResponseSpecification.chatResponse())
                .thenReturn(chatResponseWithToolCalls(List.of()));
        SpringAiLlmModelClient modelClient = new SpringAiLlmModelClient(platformChatClient);
        LlmChatRequest request =
                new LlmChatRequest(
                        "system",
                        "text-generation-user-message",
                        List.of(),
                        "openai/gpt-5.4-nano",
                        0.2,
                        128,
                        false);

        modelClient.call(request);

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

    private LlmChatRequest request() {
        List<LlmTool> tools = new AllowListedTools().tools();
        return new LlmChatRequest(
                SystemPrompts.TRIAGE_SYSTEM_PROMPT,
                "sanitized-user-message",
                tools,
                "openai/gpt-5.4-nano",
                0.0,
                true);
    }

    private ChatResponse chatResponseWithToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        AssistantMessage assistantMessage = AssistantMessage.builder().toolCalls(toolCalls).build();
        return ChatResponse.builder()
                .generations(List.of(new Generation(assistantMessage)))
                .build();
    }
}
