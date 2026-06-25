package com.zeromail.core.chat.llm.springai;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.chat.domain.ChatMessage;
import com.zeromail.core.chat.domain.ChatRole;
import com.zeromail.core.chat.domain.parts.ChatMessageParts;
import com.zeromail.core.chat.domain.parts.TextPart;
import com.zeromail.core.chat.domain.parts.ToolCallPart;
import com.zeromail.core.chat.domain.parts.ToolOutputPart;
import com.zeromail.core.chat.usecases.ChatStreamRequest;
import com.zeromail.core.chat.usecases.ChatToolCatalog;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.json.JsonMapper;

class SpringAiStreamingChatModelClientPromptTest {

    private static final String BODY_SENTINEL = "EMAIL_BODY_VISIBLE_TO_MODEL_ONLY";

    @Test
    void prompt_uses_transient_read_tool_response_when_present() {
        UUID tenantId = UUID.randomUUID();
        String toolCallId = "tool-read-message";
        ChatMessage persistedSanitizedToolMessage =
                new ChatMessage(
                        null,
                        UUID.randomUUID(),
                        tenantId,
                        ChatRole.TOOL.id(),
                        ChatMessageParts.v1(
                                List.of(
                                        new ToolOutputPart(
                                                "tool-output-" + toolCallId,
                                                toolCallId,
                                                "getMessage",
                                                "output-available",
                                                Map.of("messageId", "message-1"),
                                                false))),
                        Instant.now());
        ChatStreamRequest streamRequest =
                new ChatStreamRequest(
                        tenantId.toString(),
                        UUID.randomUUID(),
                        "test-model",
                        "system",
                        new ChatToolCatalog(),
                        List.of(persistedSanitizedToolMessage),
                        Map.of(
                                toolCallId,
                                "{\"messageId\":\"message-1\",\"bodyText\":\""
                                        + BODY_SENTINEL
                                        + "\"}"));
        SpringAiStreamingChatModelClient client =
                new SpringAiStreamingChatModelClient(
                        null,
                        null,
                        new ToolCallbackTranslator(),
                        new ChatToolCatalog(),
                        JsonMapper.builder().build());

        Prompt prompt = client.prompt(streamRequest);

        ToolResponseMessage toolResponseMessage =
                (ToolResponseMessage) prompt.getInstructions().get(1);
        assertThat(toolResponseMessage.getResponses().getFirst().responseData())
                .contains(BODY_SENTINEL);
    }

    @Test
    void prompt_injects_synthetic_tool_response_for_dangling_send_tool_call() {
        // Reproduces the production "chat dies after sending mail" bug: a confirmed send/reply/
        // forward persists an ASSISTANT tool_call with no matching TOOL output, so the next turn
        // replays a dangling tool_call and the provider rejects it with messages.[N].role.
        UUID tenantId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        String toolCallId = "call-send-1";

        ChatMessage userAsk =
                new ChatMessage(
                        null,
                        chatId,
                        tenantId,
                        ChatRole.USER.id(),
                        ChatMessageParts.v1(List.of(new TextPart("user-1", "send mail to alice"))),
                        Instant.now());
        ChatMessage assistantSendProposal =
                new ChatMessage(
                        null,
                        chatId,
                        tenantId,
                        ChatRole.ASSISTANT.id(),
                        ChatMessageParts.v1(
                                List.of(
                                        new ToolCallPart(
                                                "call-part-1",
                                                toolCallId,
                                                "send_email",
                                                "complete",
                                                Map.of("to", "alice@example.com"),
                                                false))),
                        Instant.now());
        ChatMessage userFollowUp =
                new ChatMessage(
                        null,
                        chatId,
                        tenantId,
                        ChatRole.USER.id(),
                        ChatMessageParts.v1(List.of(new TextPart("user-2", "thanks"))),
                        Instant.now());

        ChatStreamRequest streamRequest =
                new ChatStreamRequest(
                        tenantId.toString(),
                        chatId,
                        "test-model",
                        "system",
                        new ChatToolCatalog(),
                        List.of(userAsk, assistantSendProposal, userFollowUp));
        SpringAiStreamingChatModelClient client =
                new SpringAiStreamingChatModelClient(
                        null,
                        null,
                        new ToolCallbackTranslator(),
                        new ChatToolCatalog(),
                        JsonMapper.builder().build());

        Prompt prompt = client.prompt(streamRequest);
        List<Message> instructions = prompt.getInstructions();

        // system, user, assistant(tool_call), SYNTHETIC tool response, user-follow-up
        assertThat(instructions).hasSize(5);
        assertThat(instructions.get(2)).isInstanceOf(AssistantMessage.class);
        assertThat(((AssistantMessage) instructions.get(2)).hasToolCalls()).isTrue();
        assertThat(instructions.get(3)).isInstanceOf(ToolResponseMessage.class);
        assertThat(((ToolResponseMessage) instructions.get(3)).getResponses())
                .singleElement()
                .satisfies(
                        response -> {
                            assertThat(response.id()).isEqualTo(toolCallId);
                            assertThat(response.name()).isEqualTo("send_email");
                        });
        // The dangling call is repaired BEFORE the next user turn, keeping history well-formed.
        assertThat(instructions.get(4)).isInstanceOf(UserMessage.class);
    }
}
