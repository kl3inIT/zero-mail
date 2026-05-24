package com.zeromail.core.chat.llm.springai;

import com.openai.errors.OpenAIServiceException;
import com.zeromail.core.chat.domain.ChatMessage;
import com.zeromail.core.chat.domain.ChatRole;
import com.zeromail.core.chat.domain.parts.AssistantTextPart;
import com.zeromail.core.chat.domain.parts.Part;
import com.zeromail.core.chat.domain.parts.TextPart;
import com.zeromail.core.chat.domain.parts.ToolCallPart;
import com.zeromail.core.chat.domain.parts.ToolOutputPart;
import com.zeromail.core.chat.llm.ChatToolCallRegistry;
import com.zeromail.core.chat.llm.TenantAwareReactorScheduler;
import com.zeromail.core.chat.usecases.ChatLlmGateway;
import com.zeromail.core.chat.usecases.ChatStreamRequest;
import com.zeromail.core.chat.usecases.ChatStreamSink;
import com.zeromail.core.chat.usecases.RawToolCall;
import com.zeromail.core.llm.gateway.springai.SpringAiRawToolCallSupport;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@SuppressWarnings("DuplicatedCode")
public class SpringAiStreamingChatModelClient implements ChatLlmGateway {

    private static final Logger log =
            LoggerFactory.getLogger(SpringAiStreamingChatModelClient.class);
    private static final String ASSISTANT_TEXT_PART_ID = "assistant-text";

    private final SpringAiChatModelFactory chatModelFactory;
    private final TenantAwareReactorScheduler tenantAwareReactorScheduler;
    private final ToolCallbackTranslator toolCallbackTranslator;
    private final com.zeromail.core.chat.usecases.ChatToolCatalog chatToolCatalog;
    private final ObjectMapper objectMapper;

    public SpringAiStreamingChatModelClient(
            SpringAiChatModelFactory chatModelFactory,
            TenantAwareReactorScheduler tenantAwareReactorScheduler,
            ToolCallbackTranslator toolCallbackTranslator,
            com.zeromail.core.chat.usecases.ChatToolCatalog chatToolCatalog,
            ObjectMapper objectMapper) {
        this.chatModelFactory = chatModelFactory;
        this.tenantAwareReactorScheduler = tenantAwareReactorScheduler;
        this.toolCallbackTranslator = toolCallbackTranslator;
        this.chatToolCatalog = chatToolCatalog;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void verifyCatalogContract() {
        chatToolCatalog.validate();
    }

    @Override
    public Disposable streamChat(ChatStreamRequest streamRequest, ChatStreamSink streamSink) {
        SpringAiChatModelFactory.ResolvedChatClient resolvedChatClient =
                chatModelFactory.forTenant(streamRequest.tenantId(), streamRequest.modelId());
        ChatToolCallRegistry chatToolCallRegistry = new ChatToolCallRegistry();
        Scheduler scheduler = tenantAwareReactorScheduler.scheduler();
        TextEmissionState textEmissionState = new TextEmissionState();
        Prompt prompt = prompt(streamRequest);
        log.debug(
                "event=chat_llm_stream_start tenantId={} modelId={}",
                safeLogToken(streamRequest.tenantId()),
                safeLogToken(resolvedChatClient.modelId()));

        return resolvedChatClient
                .chatClient()
                .prompt(prompt)
                .tools(
                        toolSpec ->
                                toolSpec.callbacks(
                                        toolCallbackTranslator.translate(
                                                streamRequest.toolCatalog())))
                .advisors(SpringAiRawToolCallSupport::preserveRawToolCalls)
                .options(chatModelFactory.optionsFor(resolvedChatClient))
                .stream()
                .chatResponse()
                .subscribeOn(scheduler)
                .doFinally(ignoredSignal -> scheduler.dispose())
                .subscribe(
                        chatResponse -> {
                            Generation generation = chatResponse.getResult();
                            if (generation == null) {
                                return;
                            }
                            AssistantMessage assistantMessage = generation.getOutput();
                            String textDelta = assistantMessage.getText();
                            if (textDelta != null && !textDelta.isEmpty()) {
                                if (!textEmissionState.started()) {
                                    streamSink.emitTextStart(ASSISTANT_TEXT_PART_ID);
                                    textEmissionState.start();
                                }
                                streamSink.emitTextDelta(ASSISTANT_TEXT_PART_ID, textDelta);
                            }
                            for (AssistantMessage.ToolCall toolCall :
                                    assistantMessage.getToolCalls()) {
                                chatToolCallRegistry.appendDelta(
                                        toolCall.id(), toolCall.name(), toolCall.arguments());
                            }
                        },
                        chatStreamingFailure -> {
                            Throwable rootCause = rootCause(chatStreamingFailure);
                            log.warn(
                                    "event=chat_llm_stream_failed tenantId={} errorClass={} {}",
                                    safeLogToken(streamRequest.tenantId()),
                                    rootCause.getClass().getSimpleName(),
                                    serviceExceptionSummary(rootCause));
                            streamSink.emitError(
                                    "chat_stream_failed", "The assistant stream failed.");
                        },
                        () -> {
                            if (textEmissionState.started()) {
                                streamSink.emitTextEnd(ASSISTANT_TEXT_PART_ID);
                            }
                            for (RawToolCall rawToolCall :
                                    chatToolCallRegistry.finalizeToolCalls()) {
                                streamSink.emitToolInputStart(
                                        rawToolCall.toolCallId(), rawToolCall.toolName());
                                streamSink.emitToolInputAvailable(
                                        rawToolCall.toolCallId(),
                                        rawToolCall.toolName(),
                                        rawToolCall.argsJson());
                            }
                            streamSink.emitFinish("stop");
                        });
    }

    Prompt prompt(ChatStreamRequest streamRequest) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(streamRequest.systemPrompt()));
        streamRequest.conversationHistory().stream()
                .map(
                        chatMessage ->
                                toSpringAiMessage(
                                        chatMessage,
                                        streamRequest.transientToolResponseJsonByCallId()))
                .forEach(messages::add);
        return new Prompt(messages);
    }

    private Message toSpringAiMessage(
            ChatMessage chatMessage, Map<String, String> transientToolResponseJsonByCallId) {
        if (chatMessage.role() == ChatRole.USER) {
            return new UserMessage(textContent(chatMessage));
        }
        if (chatMessage.role() == ChatRole.SYSTEM) {
            return new SystemMessage(textContent(chatMessage));
        }
        if (chatMessage.role() == ChatRole.TOOL) {
            return toolResponseMessage(chatMessage, transientToolResponseJsonByCallId);
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

    private ToolResponseMessage toolResponseMessage(
            ChatMessage chatMessage, Map<String, String> transientToolResponseJsonByCallId) {
        List<ToolResponseMessage.ToolResponse> responses =
                chatMessage.parts().parts().stream()
                        .filter(ToolOutputPart.class::isInstance)
                        .map(ToolOutputPart.class::cast)
                        .map(
                                toolOutputPart ->
                                        new ToolResponseMessage.ToolResponse(
                                                toolOutputPart.toolCallId(),
                                                toolOutputPart.toolName(),
                                                transientToolResponseJsonByCallId.getOrDefault(
                                                        toolOutputPart.toolCallId(),
                                                        writeJson(toolOutputPart.outputJson()))))
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException jacksonException) {
            throw new IllegalStateException(
                    "chat prompt tool payload could not be serialized", jacksonException);
        }
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
        return "statusCode="
                + openAIServiceException.statusCode()
                + " code="
                + safeLogToken(openAIServiceException.code().orElse("-"))
                + " param="
                + safeLogToken(openAIServiceException.param().orElse("-"))
                + " type="
                + safeLogToken(openAIServiceException.type().orElse("-"));
    }

    private static String safeLogToken(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        StringBuilder sanitizedValue = new StringBuilder(Math.min(value.length(), 120));
        for (int index = 0; index < value.length() && sanitizedValue.length() < 120; index++) {
            char character = value.charAt(index);
            sanitizedValue.append(Character.isISOControl(character) ? '_' : character);
        }
        return sanitizedValue.toString();
    }

    private static final class TextEmissionState {
        private boolean started;

        boolean started() {
            return started;
        }

        void start() {
            started = true;
        }
    }
}
