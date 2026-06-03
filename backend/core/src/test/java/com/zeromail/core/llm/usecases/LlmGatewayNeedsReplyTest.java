package com.zeromail.core.llm.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.llm.config.LlmProperties.PlatformProperties;
import com.zeromail.core.llm.domain.ActionValidator;
import com.zeromail.core.llm.domain.AllowListedTools;
import com.zeromail.core.llm.gateway.sanitization.SanitizationPipeline;
import com.zeromail.core.llm.gateway.sanitization.Sanitizer;
import com.zeromail.core.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LlmGatewayNeedsReplyTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000060e1");

    @Test
    void reply_verdict_marks_message_as_needing_reply_and_uses_the_needs_reply_prompt()
            throws Exception {
        RecordingLlmModelClient modelClient = modelClientReturning("REPLY");
        LlmGateway gateway = gateway(modelClient);

        boolean replyNeeded = classifyWithTenant(gateway, "sender asks a direct question");

        assertThat(replyNeeded).isTrue();
        // System prompt is gateway-owned; callers never pass it.
        assertThat(modelClient.lastRequest().systemPrompt())
                .isEqualTo(SystemPrompts.NEEDS_REPLY_SYSTEM_PROMPT);
    }

    @Test
    void fyi_verdict_marks_message_as_not_needing_reply() throws Exception {
        LlmGateway gateway = gateway(modelClientReturning("FYI"));

        assertThat(classifyWithTenant(gateway, "monthly newsletter")).isFalse();
    }

    @Test
    void fyi_verdict_tolerates_trailing_text() throws Exception {
        LlmGateway gateway = gateway(modelClientReturning("FYI — no reply expected"));

        assertThat(classifyWithTenant(gateway, "delivery notification")).isFalse();
    }

    @Test
    void ambiguous_verdict_fails_open_to_needing_reply() throws Exception {
        LlmGateway gateway = gateway(modelClientReturning("not sure"));

        assertThat(classifyWithTenant(gateway, "ambiguous content")).isTrue();
    }

    private boolean classifyWithTenant(LlmGateway gateway, String sanitizedContent)
            throws Exception {
        return ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                .call(() -> gateway.classifyReplyNeeded(CallSite.NEEDS_REPLY, sanitizedContent));
    }

    private static RecordingLlmModelClient modelClientReturning(String assistantText) {
        return new RecordingLlmModelClient(
                new LlmChatResult(List.of(), new LlmUsage(12, 1, "stop"), assistantText));
    }

    private LlmGateway gateway(LlmModelClient modelClient) {
        return new LlmGatewayImpl(
                modelClient,
                new SanitizationPipeline(List.of(new FixedSanitizer())),
                llmProperties(),
                new AllowListedTools(),
                new ActionValidator());
    }

    private PlatformProperties llmProperties() {
        return new PlatformProperties(
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
