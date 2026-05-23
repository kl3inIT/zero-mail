package com.zeromail.core.chat.llm.springai;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.chat.domain.ChatMessage;
import com.zeromail.core.chat.domain.ChatRole;
import com.zeromail.core.chat.domain.parts.ChatMessageParts;
import com.zeromail.core.chat.domain.parts.ToolOutputPart;
import com.zeromail.core.chat.usecases.ChatStreamRequest;
import com.zeromail.core.chat.usecases.ChatToolCatalog;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.ToolResponseMessage;
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
}
