package com.zeromail.core.llm.usecases;

import static org.assertj.core.api.Assertions.assertThat;

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

class LlmGatewayDigestSummaryTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000060d2");

    @Test
    void summarizeDigestItems_parses_lines_and_maps_summaries_back_to_source_refs()
            throws Exception {
        RecordingLlmModelClient modelClient =
                new RecordingLlmModelClient(
                        new LlmChatResult(
                                List.of(),
                                new LlmUsage(20, 10, "stop"),
                                "[1] Invoice 4021 is due Friday.\n[2] Offsite moved to the 14th."));
        LlmGateway gateway = gateway(modelClient);

        List<DigestSummaryLine> summaries =
                summarizeWithTenant(
                        gateway,
                        List.of(
                                new DigestSummarySource("msg-a", "Invoice body"),
                                new DigestSummarySource("msg-b", "Offsite body")));

        assertThat(summaries)
                .containsExactly(
                        new DigestSummaryLine("msg-a", "Invoice 4021 is due Friday."),
                        new DigestSummaryLine("msg-b", "Offsite moved to the 14th."));
        // System prompt is gateway-owned; callers never pass it.
        assertThat(modelClient.lastRequest().systemPrompt())
                .isEqualTo(SystemPrompts.DIGEST_SUMMARY_SYSTEM_PROMPT);
    }

    @Test
    void summarizeDigestItems_skips_malformed_and_out_of_range_lines() throws Exception {
        RecordingLlmModelClient modelClient =
                new RecordingLlmModelClient(
                        new LlmChatResult(
                                List.of(),
                                new LlmUsage(20, 10, "stop"),
                                """
                                Here are your summaries:
                                [1] Valid summary.
                                [9] Out of range, dropped.
                                [2]
                                garbage line
                                """));
        LlmGateway gateway = gateway(modelClient);

        List<DigestSummaryLine> summaries =
                summarizeWithTenant(
                        gateway,
                        List.of(
                                new DigestSummarySource("msg-a", "body a"),
                                new DigestSummarySource("msg-b", "body b")));

        assertThat(summaries).containsExactly(new DigestSummaryLine("msg-a", "Valid summary."));
    }

    @Test
    void summarizeDigestItems_returns_empty_without_a_model_call_for_empty_input()
            throws Exception {
        RecordingLlmModelClient modelClient =
                new RecordingLlmModelClient(
                        new LlmChatResult(List.of(), new LlmUsage(0, 0, "stop"), ""));
        LlmGateway gateway = gateway(modelClient);

        List<DigestSummaryLine> summaries = summarizeWithTenant(gateway, List.of());

        assertThat(summaries).isEmpty();
        assertThat(modelClient.lastRequest()).isNull();
    }

    private List<DigestSummaryLine> summarizeWithTenant(
            LlmGateway gateway, List<DigestSummarySource> sources) throws Exception {
        return ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                .call(() -> gateway.summarizeDigestItems(sources));
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
