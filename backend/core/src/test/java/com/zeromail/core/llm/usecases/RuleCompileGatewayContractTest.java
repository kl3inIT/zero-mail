package com.zeromail.core.llm.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.config.ZeroMailCoreProperties.ZeroMailLlmProperties;
import com.zeromail.core.llm.domain.ActionValidator;
import com.zeromail.core.llm.domain.AllowListedTools;
import com.zeromail.core.llm.domain.BYOKProvider;
import com.zeromail.core.llm.gateway.sanitization.SanitizationPipeline;
import com.zeromail.core.llm.gateway.sanitization.Sanitizer;
import com.zeromail.core.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RuleCompileGatewayContractTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000044");

    @Test
    void compileRule_preview_uses_rule_compile_profile_and_returns_dedicated_result()
            throws Exception {
        RecordingLlmModelClient recordingModelClient =
                new RecordingLlmModelClient(
                        new LlmChatResult(
                                List.of(
                                        new RawToolCall(
                                                "rule_compile",
                                                "{\"schemaVersion\":\"rules.v1\",\"displayName\":\"Stripe receipts\"}")),
                                new LlmUsage(10, 5, "stop")));
        LlmGateway gateway = gateway(recordingModelClient);

        RuleCompileGatewayResult compileResult =
                ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                        .call(
                                () ->
                                        gateway.compileRule(
                                                CallSite.PREVIEW, "Archive Stripe receipts"));

        assertThat(compileResult.toolName()).isEqualTo("rule_compile");
        assertThat(compileResult.modelId()).isEqualTo("openai/gpt-5.4-nano");
        assertThat(compileResult.toolArguments())
                .containsEntry("schemaVersion", "rules.v1")
                .containsEntry("displayName", "Stripe receipts");
        assertThat(recordingModelClient.lastRequest())
                .satisfies(
                        request -> {
                            assertThat(request.systemPrompt())
                                    .isEqualTo(SystemPrompts.RULE_COMPILE_SYSTEM_PROMPT);
                            assertThat(request.userMessage())
                                    .isEqualTo("sanitized-compiler-payload");
                            assertThat(request.tools())
                                    .singleElement()
                                    .satisfies(
                                            tool ->
                                                    assertThat(tool.name())
                                                            .isEqualTo("rule_compile"));
                            assertThat(request.model()).isEqualTo("openai/gpt-5.4-nano");
                            assertThat(request.temperature()).isZero();
                            assertThat(request.toolChoiceRequired()).isTrue();
                        });
    }

    @Test
    void compileRule_rejects_non_preview_call_site_before_model_call() {
        RecordingLlmModelClient recordingModelClient =
                new RecordingLlmModelClient(
                        new LlmChatResult(
                                List.of(new RawToolCall("rule_compile", "{}")),
                                new LlmUsage(1, 1, "stop")));
        LlmGateway gateway = gateway(recordingModelClient);

        assertThatThrownBy(
                        () ->
                                ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                                        .run(
                                                () ->
                                                        gateway.compileRule(
                                                                CallSite.TRIAGE,
                                                                "Archive receipts")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PREVIEW");
        assertThat(recordingModelClient.lastRequest()).isNull();
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
                "openai/gpt-5.4-nano",
                "openai/gpt-5.4-nano",
                "openai/gpt-5.4-nano",
                null,
                null);
    }

    private static final class FixedSanitizer implements Sanitizer {

        @Override
        public SanitizationContext apply(SanitizationContext context) {
            return new SanitizationContext("sanitized-compiler-payload", 3, false, null);
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
