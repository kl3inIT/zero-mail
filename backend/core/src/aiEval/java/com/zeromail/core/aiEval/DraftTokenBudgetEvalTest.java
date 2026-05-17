package com.zeromail.core.aiEval;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.config.ZeroMailCoreProperties.ZeroMailLlmProperties;
import com.zeromail.core.draft.usecases.DraftBodyGenerator;
import com.zeromail.core.draft.usecases.ToneContextBuilder;
import com.zeromail.core.llm.domain.Action;
import com.zeromail.core.llm.domain.ActionValidator;
import com.zeromail.core.llm.domain.AllowListedTools;
import com.zeromail.core.llm.domain.BYOKProvider;
import com.zeromail.core.llm.exception.TokenBudgetExceededException;
import com.zeromail.core.llm.gateway.sanitization.SanitizationPipeline;
import com.zeromail.core.llm.gateway.sanitization.Sanitizer;
import com.zeromail.core.llm.usecases.LlmChatRequest;
import com.zeromail.core.llm.usecases.LlmChatResult;
import com.zeromail.core.llm.usecases.LlmGateway;
import com.zeromail.core.llm.usecases.LlmModelClient;
import com.zeromail.core.llm.usecases.LlmUsage;
import com.zeromail.core.llm.usecases.RawToolCall;
import com.zeromail.core.llm.usecases.SanitizationContext;
import com.zeromail.core.llm.usecases.ToolCallResult;
import com.zeromail.core.tenant.TenantContext;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class DraftTokenBudgetEvalTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-00000005b008");

    @Test
    void dim8_draft_gateway_always_sets_explicit_completion_cap() throws Exception {
        RecordingLlmModelClient recordingModelClient = new RecordingLlmModelClient();
        LlmGateway gateway = reflectiveGateway(recordingModelClient);

        ToolCallResult result =
                ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                        .call(
                                () ->
                                        gateway.chatForDraft(
                                                CallSite.DRAFT,
                                                new SanitizationContext(
                                                        "Synthetic inbound body", 12, false, null),
                                                "averageSentenceWords=8",
                                                List.of("Synthetic style sample."),
                                                "Synthetic subject"));

        assertThat(result.action()).isEqualTo(Action.SAVE_DRAFT);
        assertThat(recordingModelClient.lastRequest())
                .satisfies(
                        request -> {
                            assertThat(request.maxTokens()).isEqualTo(700);
                            assertThat(request.temperature()).isEqualTo(0.5);
                            assertThat(request.toolChoiceRequired()).isTrue();
                            assertThat(request.tools())
                                    .singleElement()
                                    .satisfies(
                                            tool ->
                                                    assertThat(tool.name())
                                                            .isEqualTo("save_draft"));
                        });
    }

    @Test
    void dim8_token_budget_overflow_degrades_to_descriptors_only_and_draft_proceeds() {
        ToneContextBuilder toneContextBuilder =
                new ToneContextBuilder(
                        (_, _, _) -> List.of("Synthetic oversized sent-mail style sample."),
                        new SanitizationPipeline(List.of(new ThrowingTokenBudgetSanitizer())),
                        Clock.systemUTC());
        RecordingDraftGateway draftGateway = new RecordingDraftGateway();
        DraftBodyGenerator draftBodyGenerator =
                new DraftBodyGenerator(
                        new SanitizationPipeline(List.of(new PassThroughSanitizer())),
                        toneContextBuilder,
                        draftGateway);

        String draftBody =
                ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                        .call(
                                () ->
                                        draftBodyGenerator.generate(
                                                TENANT_ID,
                                                "token-budget-thread",
                                                "Synthetic inbound body",
                                                "Synthetic subject"));

        assertThat(draftBody).isEqualTo("Synthetic generated draft");
        assertThat(draftGateway.lastToneDescriptorBlock()).contains("sampleCount=1");
        assertThat(draftGateway.lastToneStyleSnippets()).isEmpty();
    }

    @Test
    void dim8_gmail_tone_fetch_failure_degrades_to_empty_tone_and_draft_proceeds() {
        ToneContextBuilder toneContextBuilder =
                new ToneContextBuilder(
                        (_, _, _) -> {
                            throw new IOException("synthetic gmail failure");
                        },
                        new SanitizationPipeline(List.of(new PassThroughSanitizer())),
                        Clock.systemUTC());
        RecordingDraftGateway draftGateway = new RecordingDraftGateway();
        DraftBodyGenerator draftBodyGenerator =
                new DraftBodyGenerator(
                        new SanitizationPipeline(List.of(new PassThroughSanitizer())),
                        toneContextBuilder,
                        draftGateway);

        String draftBody =
                ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                        .call(
                                () ->
                                        draftBodyGenerator.generate(
                                                TENANT_ID,
                                                "gmail-failure-thread",
                                                "Synthetic inbound body",
                                                "Synthetic subject"));

        assertThat(draftBody).isEqualTo("Synthetic generated draft");
        assertThat(draftGateway.lastToneDescriptorBlock()).isBlank();
        assertThat(draftGateway.lastToneStyleSnippets()).isEmpty();
    }

    @Test
    void dim8_fixture_run_reports_usage_within_completion_cap() throws Exception {
        RecordingLlmModelClient recordingModelClient = new RecordingLlmModelClient();
        LlmGateway gateway = reflectiveGateway(recordingModelClient);

        ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                .call(
                        () ->
                                gateway.chatForDraft(
                                        CallSite.DRAFT,
                                        new SanitizationContext(
                                                "Synthetic inbound body", 12, false, null),
                                        "",
                                        List.of(),
                                        "Synthetic subject"));

        assertThat(recordingModelClient.lastUsage().promptTokens()).isLessThanOrEqualTo(3896);
        assertThat(recordingModelClient.lastUsage().completionTokens()).isLessThanOrEqualTo(700);
        assertThat(recordingModelClient.lastRequest().maxTokens()).isEqualTo(700);
    }

    @Tag("judge")
    @Test
    void dim1_voice_fidelity_judge_skeleton() {
        Assumptions.assumeTrue(
                judgeKeyPresent(), "LLM judge report-only eval requires credentials");
    }

    @Tag("judge")
    @Test
    void dim2_contextual_relevance_judge_skeleton() {
        Assumptions.assumeTrue(
                judgeKeyPresent(), "LLM judge report-only eval requires credentials");
    }

    @Tag("judge")
    @Test
    void dim3_sensitivity_awareness_judge_skeleton() {
        Assumptions.assumeTrue(
                judgeKeyPresent(), "LLM judge report-only eval requires credentials");
    }

    @Tag("judge")
    @Test
    void dim5_prompt_injection_resistance_judge_skeleton() {
        Assumptions.assumeTrue(
                judgeKeyPresent(), "LLM judge report-only eval requires credentials");
    }

    private static boolean judgeKeyPresent() {
        return System.getenv("ZERO_MAIL_LLM_OPENROUTER_KEY") != null
                || System.getenv("OPENROUTER_API_KEY") != null;
    }

    private static LlmGateway reflectiveGateway(RecordingLlmModelClient recordingModelClient)
            throws Exception {
        Class<?> gatewayClass = Class.forName("com.zeromail.core.llm.usecases.LlmGatewayImpl");
        Constructor<?> constructor =
                gatewayClass.getDeclaredConstructor(
                        LlmModelClient.class,
                        SanitizationPipeline.class,
                        ZeroMailLlmProperties.class,
                        AllowListedTools.class,
                        ActionValidator.class);
        constructor.setAccessible(true);
        return (LlmGateway)
                constructor.newInstance(
                        recordingModelClient,
                        new SanitizationPipeline(List.of(new PassThroughSanitizer())),
                        new ZeroMailLlmProperties(
                                BYOKProvider.OPENAI,
                                "https://openrouter.ai/api/v1",
                                "synthetic-platform-key",
                                "openai/gpt-5.4-nano",
                                "openai/gpt-5.4-nano",
                                "openai/gpt-5.4-nano",
                                null,
                                null),
                        new AllowListedTools(),
                        new ActionValidator());
    }

    private static final class RecordingLlmModelClient implements LlmModelClient {

        private final AtomicReference<LlmChatRequest> lastRequest = new AtomicReference<>();
        private final AtomicReference<LlmUsage> lastUsage = new AtomicReference<>();

        @Override
        public LlmChatResult call(LlmChatRequest request) {
            lastRequest.set(request);
            LlmUsage usage = new LlmUsage(120, 64, "stop");
            lastUsage.set(usage);
            return new LlmChatResult(
                    List.of(
                            new RawToolCall(
                                    "save_draft", "{\"body\":\"Synthetic generated draft\"}")),
                    usage);
        }

        private LlmChatRequest lastRequest() {
            return lastRequest.get();
        }

        private LlmUsage lastUsage() {
            return lastUsage.get();
        }
    }

    private static final class RecordingDraftGateway implements LlmGateway {

        private String lastToneDescriptorBlock;
        private List<String> lastToneStyleSnippets = List.of();

        @Override
        public ToolCallResult chat(CallSite callSite, String rawHtml) {
            throw new UnsupportedOperationException("not used by draft eval");
        }

        @Override
        public ToolCallResult chatForDraft(
                CallSite callSite,
                SanitizationContext inbound,
                String toneDescriptorBlock,
                List<String> toneStyleSnippets,
                String inboundSubject) {
            lastToneDescriptorBlock = toneDescriptorBlock;
            lastToneStyleSnippets = List.copyOf(toneStyleSnippets);
            return new ToolCallResult(
                    Action.SAVE_DRAFT, Map.of("body", "Synthetic generated draft"));
        }

        @Override
        public com.zeromail.core.llm.usecases.RuleCompileGatewayResult compileRule(
                CallSite callSite, String compilerPayload) {
            throw new UnsupportedOperationException("not used by draft eval");
        }

        @Override
        public Map<String, Boolean> evaluateSemanticIntents(
                CallSite callSite,
                String rawMessageContent,
                List<com.zeromail.core.llm.usecases.SemanticIntentRequest> intents) {
            throw new UnsupportedOperationException("not used by draft eval");
        }

        @Override
        public ToolCallResult driftCheck(String rawEmailFixture) {
            throw new UnsupportedOperationException("not used by draft eval");
        }

        private String lastToneDescriptorBlock() {
            return lastToneDescriptorBlock;
        }

        private List<String> lastToneStyleSnippets() {
            return lastToneStyleSnippets;
        }
    }

    private static final class PassThroughSanitizer implements Sanitizer {

        @Override
        public SanitizationContext apply(SanitizationContext context) {
            return new SanitizationContext(context.content(), 12, false, null);
        }
    }

    private static final class ThrowingTokenBudgetSanitizer implements Sanitizer {

        @Override
        public SanitizationContext apply(SanitizationContext context) {
            throw new TokenBudgetExceededException(5000, 3896);
        }
    }
}
