package com.zeromail.core.llm.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.config.ZeroMailCoreProperties.ZeroMailLlmProperties;
import com.zeromail.core.llm.domain.Action;
import com.zeromail.core.llm.domain.ActionValidator;
import com.zeromail.core.llm.domain.AllowListedTools;
import com.zeromail.core.llm.exception.SafetyViolationException;
import com.zeromail.core.llm.gateway.sanitization.SanitizationPipeline;
import com.zeromail.core.llm.gateway.sanitization.Sanitizer;
import com.zeromail.core.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LlmGatewayActionValidatorTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000043");

    @Test
    void rejects_send_action_at_validator() {
        RecordingLlmModelClient recordingModelClient =
                new RecordingLlmModelClient(
                        new LlmChatResult(
                                List.of(new RawToolCall("send", "{\"to\":\"a@b\"}")),
                                new LlmUsage(1, 1, "stop")));
        LlmGateway gateway = gateway(recordingModelClient);

        assertThatThrownBy(() -> chatWithTenant(gateway))
                .isInstanceOf(SafetyViolationException.class);
    }

    @Test
    void accepts_label_action() {
        RecordingLlmModelClient recordingModelClient =
                new RecordingLlmModelClient(
                        new LlmChatResult(
                                List.of(new RawToolCall("label", "{\"value\":\"Receipts\"}")),
                                new LlmUsage(1, 1, "stop")));
        LlmGateway gateway = gateway(recordingModelClient);

        ToolCallResult toolCallResult = chatWithTenant(gateway);

        assertThat(toolCallResult.action()).isEqualTo(Action.LABEL);
        assertThat(toolCallResult.args()).containsEntry("value", "Receipts");
    }

    @Test
    void emits_safety_violation_log() {
        RecordingLlmModelClient recordingModelClient =
                new RecordingLlmModelClient(
                        new LlmChatResult(
                                List.of(new RawToolCall("send", "{\"to\":\"a@b\"}")),
                                new LlmUsage(1, 1, "stop")));
        LlmGateway gateway = gateway(recordingModelClient);
        ch.qos.logback.classic.Logger gatewayLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(LlmGatewayImpl.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        gatewayLogger.addAppender(listAppender);

        try {
            assertThatThrownBy(() -> chatWithTenant(gateway))
                    .isInstanceOf(SafetyViolationException.class);
        } finally {
            gatewayLogger.detachAppender(listAppender);
        }

        String formattedMessages =
                listAppender.list.stream()
                        .map(ILoggingEvent::getFormattedMessage)
                        .reduce(
                                "",
                                (combinedMessages, formattedMessage) ->
                                        combinedMessages + "\n" + formattedMessage);

        assertThat(formattedMessages)
                .contains("event=llm_safety_violation tenantId=" + TENANT_ID)
                .contains("callSite=PREVIEW")
                .contains("reason=SafetyViolationException")
                .doesNotContain("send", "to", "a@b");
    }

    @Test
    void requests_tool_choice_required() {
        RecordingLlmModelClient recordingModelClient =
                new RecordingLlmModelClient(
                        new LlmChatResult(
                                List.of(new RawToolCall("label", "{\"value\":\"Receipts\"}")),
                                new LlmUsage(1, 1, "stop")));
        LlmGateway gateway = gateway(recordingModelClient);

        chatWithTenant(gateway);

        assertThat(recordingModelClient.lastRequest())
                .satisfies(
                        request -> {
                            assertThat(request.toolChoiceRequired()).isTrue();
                            assertThat(request.systemPrompt())
                                    .isEqualTo(SystemPrompts.TRIAGE_SYSTEM_PROMPT);
                        });
    }

    @Test
    void fails_when_no_tool_call_returned() {
        RecordingLlmModelClient recordingModelClient =
                new RecordingLlmModelClient(
                        new LlmChatResult(List.of(), new LlmUsage(1, 1, "stop")));
        LlmGateway gateway = gateway(recordingModelClient);

        assertThatThrownBy(() -> chatWithTenant(gateway))
                .isInstanceOf(SafetyViolationException.class);
    }

    private ToolCallResult chatWithTenant(LlmGateway gateway) {
        return ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                .call(() -> gateway.chat(CallSite.PREVIEW, "hi"));
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
                "openai",
                "https://openrouter.ai/api/v1",
                "test-platform-key",
                "openai/gpt-5.4-nano",
                "openai/gpt-5.4-nano",
                "openai/gpt-5.4-nano",
                "openai/gpt-5.4-nano",
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
