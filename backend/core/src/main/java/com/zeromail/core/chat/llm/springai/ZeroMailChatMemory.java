package com.zeromail.core.chat.llm.springai;

import com.zeromail.core.chat.domain.ChatMessage;
import com.zeromail.core.chat.domain.ChatRole;
import com.zeromail.core.chat.domain.parts.AssistantTextPart;
import com.zeromail.core.chat.domain.parts.Part;
import com.zeromail.core.chat.domain.parts.TextPart;
import com.zeromail.core.chat.domain.parts.ToolCallPart;
import com.zeromail.core.chat.domain.parts.ToolOutputPart;
import com.zeromail.core.chat.persistence.ChatMessageJdbcRepository;
import com.zeromail.core.chat.usecases.LlmTokenizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ZeroMailChatMemory implements ChatMemory {

    private final ChatMessageJdbcRepository chatMessageRepository;
    private final LlmTokenizer llmTokenizer;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public ZeroMailChatMemory(
            ChatMessageJdbcRepository chatMessageRepository, LlmTokenizer llmTokenizer) {
        this.chatMessageRepository = chatMessageRepository;
        this.llmTokenizer = llmTokenizer;
    }

    @Override
    public void add(@NonNull String conversationId, @NonNull List<Message> messages) {
        // ChatOrchestrator owns persistence so history never depends on Spring AI's memory writes.
    }

    @Override
    public @NonNull List<Message> get(@NonNull String conversationId) {
        return loadMessages(conversationId);
    }

    public List<Message> get(String conversationId, int tokenBudget) {
        List<Message> messages = loadMessages(conversationId);
        if (tokenBudget <= 0 || messages.isEmpty()) {
            return messages.isEmpty() ? List.of() : List.of(messages.getLast());
        }
        List<Message> retainedMessages = new ArrayList<>();
        int tokenCount = 0;
        for (int messageIndex = messages.size() - 1; messageIndex >= 0; messageIndex--) {
            Message message = messages.get(messageIndex);
            int messageTokenCount = count(message);
            if (!retainedMessages.isEmpty() && tokenCount + messageTokenCount > tokenBudget) {
                break;
            }
            retainedMessages.add(message);
            tokenCount += messageTokenCount;
        }
        Collections.reverse(retainedMessages);
        return List.copyOf(retainedMessages);
    }

    @Override
    public void clear(@NonNull String conversationId) {
        // Chat history is append-only for Phase 7; clearing is a retention workflow concern.
    }

    private List<Message> loadMessages(String conversationId) {
        UUID chatId = UUID.fromString(conversationId);
        return chatMessageRepository.findByChatIdOrderByCreatedAtAsc(chatId).stream()
                .map(this::toSpringAiMessage)
                .toList();
    }

    private Message toSpringAiMessage(ChatMessage chatMessage) {
        if (chatMessage.role() == ChatRole.USER) {
            return new UserMessage(textContent(chatMessage));
        }
        if (chatMessage.role() == ChatRole.SYSTEM) {
            return new SystemMessage(textContent(chatMessage));
        }
        if (chatMessage.role() == ChatRole.TOOL) {
            return toolResponseMessage(chatMessage);
        }
        return assistantMessage(chatMessage);
    }

    private AssistantMessage assistantMessage(ChatMessage chatMessage) {
        List<AssistantMessage.ToolCall> toolCalls =
                chatMessage.parts().parts().stream()
                        .filter(ToolCallPart.class::isInstance)
                        .map(ToolCallPart.class::cast)
                        .map(
                                toolCallPart ->
                                        new AssistantMessage.ToolCall(
                                                toolCallPart.toolCallId(),
                                                "function",
                                                toolCallPart.toolName(),
                                                writeJson(toolCallPart.inputJson())))
                        .toList();
        return AssistantMessage.builder()
                .content(textContent(chatMessage))
                .toolCalls(toolCalls)
                .build();
    }

    private ToolResponseMessage toolResponseMessage(ChatMessage chatMessage) {
        List<ToolResponseMessage.ToolResponse> responses =
                chatMessage.parts().parts().stream()
                        .filter(ToolOutputPart.class::isInstance)
                        .map(ToolOutputPart.class::cast)
                        .map(
                                toolOutputPart ->
                                        new ToolResponseMessage.ToolResponse(
                                                toolOutputPart.toolCallId(),
                                                toolOutputPart.toolName(),
                                                writeJson(toolOutputPart.outputJson())))
                        .toList();
        return ToolResponseMessage.builder().responses(responses).build();
    }

    private String textContent(ChatMessage chatMessage) {
        return chatMessage.parts().parts().stream()
                .map(this::textContent)
                .filter(text -> !text.isBlank())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private String textContent(Part part) {
        if (part instanceof TextPart textPart) {
            return textPart.text();
        }
        if (part instanceof AssistantTextPart assistantTextPart) {
            return assistantTextPart.text();
        }
        return "";
    }

    private int count(Message message) {
        int tokenCount = llmTokenizer.countText(message.getText());
        if (message instanceof AssistantMessage assistantMessage) {
            for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                tokenCount += llmTokenizer.countSegments(toolCall.name(), toolCall.arguments());
            }
        }
        if (message instanceof ToolResponseMessage toolResponseMessage) {
            for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                tokenCount += llmTokenizer.countSegments(response.name(), response.responseData());
            }
        }
        return tokenCount;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException jacksonException) {
            throw new IllegalStateException(
                    "chat memory JSON payload could not be serialized", jacksonException);
        }
    }
}
