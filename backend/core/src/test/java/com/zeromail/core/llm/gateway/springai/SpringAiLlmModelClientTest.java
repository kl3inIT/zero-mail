package com.zeromail.core.llm.gateway.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.llm.application.LlmChatRequest;
import com.zeromail.core.llm.application.LlmChatResult;
import com.zeromail.core.llm.application.LlmTool;
import com.zeromail.core.llm.application.SystemPrompts;
import com.zeromail.core.llm.service.AllowListedTools;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;

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
        when(chatClientRequestSpecification.toolCallbacks(
                        ArgumentMatchers.<List<ToolCallback>>any()))
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
        verify(chatClientRequestSpecification).options(openAiChatOptionsCaptor.capture());
        OpenAiChatOptions capturedOptions = openAiChatOptionsCaptor.getValue().build();
        assertThat(capturedOptions.getToolChoice()).isEqualTo("required");
        assertThat(capturedOptions.getInternalToolExecutionEnabled()).isFalse();
        assertThat(capturedOptions.getModel()).isEqualTo("openai/gpt-4o-mini");
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
        when(chatClientRequestSpecification.toolCallbacks(
                        ArgumentMatchers.<List<ToolCallback>>any()))
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

    private LlmChatRequest request() {
        List<LlmTool> tools = new AllowListedTools().tools();
        return new LlmChatRequest(
                SystemPrompts.TRIAGE_SYSTEM_PROMPT,
                "sanitized-user-message",
                tools,
                "openai/gpt-4o-mini",
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
