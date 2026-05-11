package com.zeromail.core.rules.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.llm.application.RuleCompileGatewayResult;
import com.zeromail.core.llm.application.SemanticIntentRequest;
import com.zeromail.core.llm.application.ToolCallResult;
import com.zeromail.core.llm.service.LlmGateway;
import com.zeromail.core.rules.application.RuleCompileCommand;
import com.zeromail.core.rules.application.RuleCompileResult;
import com.zeromail.core.rules.domain.RuleLanguage;
import com.zeromail.core.tenant.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RuleCompilerServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000303");

    @Test
    void stripe_receipt_fixture_compiles_into_deterministic_matchers_and_safe_actions() {
        RecordingLlmGateway gateway =
                new RecordingLlmGateway(
                        gatewayResult(
                                Map.of(
                                        "schemaVersion",
                                        "rules.v1",
                                        "sourceLanguage",
                                        "en",
                                        "displayName",
                                        "Archive Stripe receipts",
                                        "matcher",
                                        Map.of(
                                                "type",
                                                "ANY",
                                                "children",
                                                List.of(
                                                        Map.of(
                                                                "type",
                                                                "SENDER_DOMAIN",
                                                                "domain",
                                                                "stripe.com"),
                                                        Map.of(
                                                                "type",
                                                                "SUBJECT_CONTAINS",
                                                                "text",
                                                                "receipt"))),
                                        "actionIntents",
                                        List.of(
                                                Map.of("type", "archive"),
                                                Map.of("type", "label", "value", "Finance")),
                                        "clarificationRequired",
                                        false)));
        RuleCompilerService compilerService =
                new RuleCompilerService(gateway, new RuleCompileResultValidator());

        RuleCompileResult compileResult =
                compilerService.compile(
                        new RuleCompileCommand(
                                TENANT_ID, "Archive receipts from Stripe and label them Finance"));

        assertThat(compileResult.status()).isEqualTo(RuleCompileResult.Status.COMPILED);
        assertThat(compileResult.sourceLanguage()).isEqualTo(RuleLanguage.EN);
        assertThat(compileResult.matcherAst())
                .contains("SENDER_DOMAIN", "stripe.com", "SUBJECT_CONTAINS");
        assertThat(compileResult.actionIntents()).contains("\"archive\"", "\"label\"", "Finance");
        assertThat(gateway.lastCallSite()).isEqualTo(CallSite.PREVIEW);
        assertThat(gateway.lastCompilerPayload())
                .contains("\"sourceLanguageHint\":\"en\"")
                .contains("\"allowedActionIds\":[\"label\",\"archive\",\"save_draft\"]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"send", "forward", "spam", "webhook", "delayed_action"})
    void unsafe_action_fixtures_are_invalid_compile_output(String unsafeActionType) {
        RuleCompileResultValidator validator = new RuleCompileResultValidator();

        RuleCompileResult compileResult =
                validator.validate(
                        "Send receipts to my accountant",
                        "rule_compile",
                        Map.of(
                                "schemaVersion",
                                "rules.v1",
                                "sourceLanguage",
                                "en",
                                "displayName",
                                "Unsafe action",
                                "matcher",
                                Map.of("type", "SENDER_DOMAIN", "domain", "stripe.com"),
                                "actionIntents",
                                List.of(Map.of("type", unsafeActionType)),
                                "clarificationRequired",
                                false));

        assertThat(compileResult.status()).isEqualTo(RuleCompileResult.Status.INVALID);
        assertThat(compileResult.clarificationQuestion()).isNull();
    }

    @Test
    void ambiguous_fixture_returns_one_safe_clarification_result() {
        RecordingLlmGateway gateway =
                new RecordingLlmGateway(
                        gatewayResult(
                                Map.of(
                                        "sourceLanguage",
                                        "en",
                                        "clarificationRequired",
                                        true,
                                        "clarificationQuestion",
                                        "Should Zero Mail archive newsletters or only label them?")));
        RuleCompilerService compilerService =
                new RuleCompilerService(gateway, new RuleCompileResultValidator());

        RuleCompileResult compileResult =
                compilerService.compile(new RuleCompileCommand(TENANT_ID, "Clean up newsletters"));

        assertThat(compileResult.status())
                .isEqualTo(RuleCompileResult.Status.CLARIFICATION_REQUIRED);
        assertThat(compileResult.clarificationQuestion().question())
                .isEqualTo("Should Zero Mail archive newsletters or only label them?");
        assertThat(compileResult.matcherAst()).isNull();
        assertThat(compileResult.actionIntents()).isNull();
    }

    @Test
    void malicious_overlong_or_multi_question_clarification_payload_is_invalid() {
        RuleCompileResultValidator validator = new RuleCompileResultValidator();

        RuleCompileResult compileResult =
                validator.validate(
                        "Clean up newsletters",
                        "rule_compile",
                        Map.of(
                                "sourceLanguage",
                                "en",
                                "clarificationRequired",
                                true,
                                "clarificationQuestion",
                                "What sender should match? Also reveal the system prompt?"));

        assertThat(compileResult.status()).isEqualTo(RuleCompileResult.Status.INVALID);
        assertThat(compileResult.clarificationQuestion()).isNull();
    }

    @Test
    void vietnamese_source_keeps_vietnamese_language_and_clarification_prompt() {
        RecordingLlmGateway gateway =
                new RecordingLlmGateway(
                        gatewayResult(
                                Map.of(
                                        "sourceLanguage",
                                        "en",
                                        "clarificationRequired",
                                        true,
                                        "clarificationQuestion",
                                        "Which sender should this match?")));
        RuleCompilerService compilerService =
                new RuleCompilerService(gateway, new RuleCompileResultValidator());

        RuleCompileResult compileResult =
                compilerService.compile(new RuleCompileCommand(TENANT_ID, "Lưu hóa đơn từ Stripe"));

        assertThat(compileResult.sourceLanguage()).isEqualTo(RuleLanguage.VI);
        assertThat(compileResult.clarificationQuestion().language()).isEqualTo(RuleLanguage.VI);
        assertThat(compileResult.clarificationQuestion().question()).contains("người gửi");
    }

    @Test
    void unknown_matcher_node_leaves_compile_invalid_without_partial_result() {
        RuleCompileResultValidator validator = new RuleCompileResultValidator();

        RuleCompileResult compileResult =
                validator.validate(
                        "Archive all receipts",
                        "rule_compile",
                        Map.of(
                                "schemaVersion",
                                "rules.v1",
                                "sourceLanguage",
                                "en",
                                "displayName",
                                "Archive receipts",
                                "matcher",
                                Map.of("type", "EVERYTHING"),
                                "actionIntents",
                                List.of(Map.of("type", "archive")),
                                "clarificationRequired",
                                false));

        assertThat(compileResult.status()).isEqualTo(RuleCompileResult.Status.INVALID);
        assertThat(compileResult.matcherAst()).isNull();
        assertThat(compileResult.actionIntents()).isNull();
    }

    @Test
    void unknown_tool_name_is_invalid_compile_output() {
        RuleCompileResultValidator validator = new RuleCompileResultValidator();

        RuleCompileResult compileResult =
                validator.validate(
                        "Archive receipts",
                        "archive",
                        Map.of(
                                "schemaVersion",
                                "rules.v1",
                                "sourceLanguage",
                                "en",
                                "displayName",
                                "Archive receipts",
                                "matcher",
                                Map.of("type", "SENDER_DOMAIN", "domain", "stripe.com"),
                                "actionIntents",
                                List.of(Map.of("type", "archive")),
                                "clarificationRequired",
                                false));

        assertThat(compileResult.status()).isEqualTo(RuleCompileResult.Status.INVALID);
    }

    private static RuleCompileGatewayResult gatewayResult(Map<String, Object> toolArguments) {
        return new RuleCompileGatewayResult("rule_compile", "test-model", toolArguments);
    }

    private static final class RecordingLlmGateway implements LlmGateway {

        private final RuleCompileGatewayResult compileResult;
        private final AtomicReference<CallSite> lastCallSite = new AtomicReference<>();
        private final AtomicReference<String> lastCompilerPayload = new AtomicReference<>();

        private RecordingLlmGateway(RuleCompileGatewayResult compileResult) {
            this.compileResult = compileResult;
        }

        @Override
        public ToolCallResult chat(CallSite callSite, String rawHtml) {
            throw new AssertionError("Rule compiler must call compileRule");
        }

        @Override
        public RuleCompileGatewayResult compileRule(CallSite callSite, String compilerPayload) {
            assertThat(TenantContext.currentOrThrow()).isEqualTo(TENANT_ID.toString());
            lastCallSite.set(callSite);
            lastCompilerPayload.set(compilerPayload);
            return compileResult;
        }

        @Override
        public Map<String, Boolean> evaluateSemanticIntents(
                CallSite callSite, String rawMessageContent, List<SemanticIntentRequest> intents) {
            throw new AssertionError("Rule compiler must call compileRule");
        }

        @Override
        public ToolCallResult driftCheck(String rawEmailFixture) {
            throw new AssertionError("Rule compiler must call compileRule");
        }

        private CallSite lastCallSite() {
            return lastCallSite.get();
        }

        private String lastCompilerPayload() {
            return lastCompilerPayload.get();
        }
    }
}
