package com.zeromail.core.llm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zeromail.core.billing.model.CallSite;
import com.zeromail.core.llm.gateway.sanitization.SanitizationPipeline;
import com.zeromail.core.llm.gateway.sanitization.Sanitizer;
import com.zeromail.core.config.ZeroMailCoreProperties.ZeroMailLlmProperties;
import com.zeromail.core.llm.model.Action;
import com.zeromail.core.llm.model.BYOKProvider;
import com.zeromail.core.llm.model.LlmChatRequest;
import com.zeromail.core.llm.model.LlmChatResult;
import com.zeromail.core.llm.model.LlmUsage;
import com.zeromail.core.llm.model.RawToolCall;
import com.zeromail.core.llm.model.SanitizationContext;
import com.zeromail.core.llm.model.SystemPrompts;
import com.zeromail.core.llm.model.ToolCallResult;
import com.zeromail.core.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LlmGatewayPlatformPathTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");

    @Test
    void chat_sanitizes_then_returns_first_tool_call_result() throws Exception {
        RecordingLlmModelClient recordingModelClient = new RecordingLlmModelClient(
                new LlmChatResult(
                        List.of(new RawToolCall("label", "{\"value\":\"Receipts\"}")),
                        new LlmUsage(10, 5, "stop")));
        LlmGateway gateway = gateway(recordingModelClient);

        ToolCallResult toolCallResult = chatWithTenant(gateway, "<p>hi</p>");

        assertThat(toolCallResult.action()).isEqualTo(Action.LABEL);
        assertThat(toolCallResult.args()).containsEntry("value", "Receipts");
        assertThat(recordingModelClient.lastRequest())
                .satisfies(request -> {
                    assertThat(request.systemPrompt()).isEqualTo(SystemPrompts.TRIAGE_SYSTEM_PROMPT);
                    assertThat(request.userMessage()).isEqualTo("sanitized-user-message");
                    assertThat(request.tools()).hasSize(3);
                    assertThat(request.model()).isEqualTo("openai/gpt-4o-mini");
                    assertThat(request.temperature()).isZero();
                    assertThat(request.toolChoiceRequired()).isTrue();
                });
    }

    @Test
    void emits_privacy_log_on_success_without_input_or_output_content() throws Exception {
        RecordingLlmModelClient recordingModelClient = new RecordingLlmModelClient(
                new LlmChatResult(
                        List.of(new RawToolCall("label", "{\"value\":\"Receipts\"}")),
                        new LlmUsage(10, 5, "stop")));
        LlmGateway gateway = gateway(recordingModelClient);
        ch.qos.logback.classic.Logger gatewayLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(LlmGatewayImpl.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        gatewayLogger.addAppender(listAppender);

        try {
            chatWithTenant(gateway, "<p>hi</p>");
        } finally {
            gatewayLogger.detachAppender(listAppender);
        }

        String formattedMessages = listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (combinedMessages, formattedMessage) -> combinedMessages + "\n" + formattedMessage);

        assertThat(formattedMessages)
                .contains("event=llm_call_succeeded tenantId=" + TENANT_ID)
                .contains("callSite=PREVIEW")
        .contains("provider=openai")
                .contains("model=openai/gpt-4o-mini")
                .contains("latencyMs=")
                .contains("promptTokens=10")
                .contains("completionTokens=5")
                .contains("stopReason=stop")
                .contains("truncated=false")
                .doesNotContain("<p>", "hi", "Receipts", "sanitized-user-message");
    }

    private ToolCallResult chatWithTenant(LlmGateway gateway, String rawHtml) throws Exception {
        return ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                .call(() -> gateway.chat(CallSite.PREVIEW, rawHtml));
    }

    private LlmGateway gateway(LlmModelClient modelClient) {
        return new LlmGatewayImpl(
                modelClient,
                new SanitizationPipeline(List.of(new FixedSanitizer())),
                llmProperties(),
                new AllowListedTools(),
                new ActionValidator());
    }

    private ZeroMailLlmProperties llmProperties() {
        return new ZeroMailLlmProperties(
        BYOKProvider.OPENAI,
                "https://openrouter.ai/api/v1",
                "test-platform-key",
                "openai/gpt-4o-mini",
                "openai/gpt-4o-mini",
                "openai/gpt-4o-mini",
                null,
                null);
    }

    private static final class FixedSanitizer implements Sanitizer {

        @Override
        public SanitizationContext apply(SanitizationContext context) {
            return new SanitizationContext("sanitized-user-message", 3, false, null);
        }
    }

    private static final class RecordingLlmModelClient implements LlmModelClient {

        private final LlmChatResult result;
        private final AtomicReference<LlmChatRequest> lastRequest = new AtomicReference<>();

        private RecordingLlmModelClient(LlmChatResult result) {
            this.result = result;
        }

        @Override
        public LlmChatResult call(LlmChatRequest request) {
            lastRequest.set(request);
            return result;
        }

        private LlmChatRequest lastRequest() {
            return lastRequest.get();
        }
    }
}
