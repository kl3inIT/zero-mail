package com.zeromail.core.llm.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.config.ZeroMailCoreProperties.ZeroMailLlmProperties;
import com.zeromail.core.llm.domain.ActionValidator;
import com.zeromail.core.llm.domain.AllowListedTools;
import com.zeromail.core.llm.domain.LlmToolProfile;
import com.zeromail.core.llm.domain.RuleCompileToolValidator;
import com.zeromail.core.llm.exception.SafetyViolationException;
import com.zeromail.core.llm.gateway.sanitization.SanitizationPipeline;
import com.zeromail.core.llm.gateway.sanitization.Sanitizer;
import com.zeromail.core.tenant.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class RuleCompileToolProfileTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000045");

    @Test
    void safe_actions_profile_remains_safe_action_tools_only() {
        List<LlmTool> tools = new AllowListedTools().tools(LlmToolProfile.SAFE_ACTIONS);

        assertThat(tools)
                .extracting(LlmTool::name)
                .containsExactly("label", "archive", "save_draft");
    }

    @Test
    void rule_compile_profile_contains_exactly_rule_compile_tool_with_schema() {
        List<LlmTool> tools = new AllowListedTools().tools(LlmToolProfile.RULE_COMPILE);

        assertThat(tools)
                .singleElement()
                .satisfies(tool -> assertThat(tool.name()).isEqualTo("rule_compile"));
        Map<String, Object> schema = tools.getFirst().jsonSchema();
        Map<String, Object> properties = map(schema.get("properties"));
        Map<String, Object> schemaVersion = map(properties.get("schemaVersion"));
        Map<String, Object> sourceLanguage = map(properties.get("sourceLanguage"));
        Map<String, Object> actionIntents = map(properties.get("actionIntents"));
        Map<String, Object> actionIntentItems = map(actionIntents.get("items"));
        Map<String, Object> actionIntentProperties = map(actionIntentItems.get("properties"));
        Map<String, Object> actionType = map(actionIntentProperties.get("type"));

        assertThat(schema)
                .containsEntry("type", "object")
                .containsEntry("additionalProperties", false);
        assertThat(schemaVersion).containsEntry("const", "rules.v1");
        assertThat(sourceLanguage.get("enum")).isEqualTo(List.of("en", "vi", "unknown"));
        assertThat(properties)
                .containsKeys(
                        "displayName",
                        "matcher",
                        "actionIntents",
                        "clarificationRequired",
                        "clarificationQuestion");
        assertThat(actionType.get("enum"))
                .isEqualTo(
                        List.of(
                                "label",
                                "archive",
                                "save_draft",
                                "mark_read",
                                "star",
                                "add_to_digest",
                                "mark_spam",
                                "send_reply",
                                "forward_email",
                                "send_email"));
        assertThat(actionIntentProperties)
                .containsKeys("recipients", "to", "cc", "bcc", "subject", "body", "instruction");
    }

    @Test
    void rule_compile_review_draft_profile_removes_clarification_branch() {
        List<LlmTool> tools =
                new AllowListedTools().tools(LlmToolProfile.RULE_COMPILE_REVIEW_DRAFT);

        assertThat(tools)
                .singleElement()
                .satisfies(tool -> assertThat(tool.name()).isEqualTo("rule_compile"));
        Map<String, Object> schema = tools.getFirst().jsonSchema();
        Map<String, Object> properties = map(schema.get("properties"));
        Map<String, Object> clarificationRequired = map(properties.get("clarificationRequired"));

        assertThat(schema)
                .containsEntry("type", "object")
                .containsEntry("additionalProperties", false);
        assertThat(clarificationRequired).containsEntry("const", false);
        assertThat(properties).doesNotContainKey("clarificationQuestion");
    }

    @Test
    void rule_compile_validator_accepts_only_canonical_tool_name() {
        RuleCompileToolValidator validator = new RuleCompileToolValidator();

        assertThat(validator.validate("rule_compile")).isEqualTo("rule_compile");
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(SafetyViolationException.class);
        assertThatThrownBy(() -> validator.validate(" "))
                .isInstanceOf(SafetyViolationException.class);
        assertThatThrownBy(() -> validator.validate("label"))
                .isInstanceOf(SafetyViolationException.class);
        assertThatThrownBy(() -> validator.validate("send"))
                .isInstanceOf(SafetyViolationException.class);
    }

    @Test
    void compileRule_rejects_unknown_tool_without_logging_tool_arguments() {
        String sentinel = "PRIVATE_RULE_COMPILE_ARGUMENT_SHOULD_NOT_LOG";
        LlmGateway gateway =
                gateway(
                        _ ->
                                new LlmChatResult(
                                        List.of(
                                                new RawToolCall(
                                                        "send",
                                                        "{\"private\":\""
                                                                + sentinel
                                                                + "\",\"tool\":\"send\"}")),
                                        new LlmUsage(1, 1, "stop")));
        ch.qos.logback.classic.Logger gatewayLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(LlmGatewayImpl.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        gatewayLogger.addAppender(listAppender);

        try {
            assertThatThrownBy(
                            () ->
                                    ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                                            .run(
                                                    () ->
                                                            gateway.compileRule(
                                                                    CallSite.PREVIEW,
                                                                    "Archive receipts")))
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
                .doesNotContain(sentinel, "private", "send");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
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
                null,
                null);
    }

    private static final class FixedSanitizer implements Sanitizer {

        @Override
        public SanitizationContext apply(SanitizationContext context) {
            return new SanitizationContext("sanitized-compiler-payload", 3, false, null);
        }
    }

    @FunctionalInterface
    private interface LlmModelClient extends com.zeromail.core.llm.usecases.LlmModelClient {

        @Override
        LlmChatResult call(LlmChatRequest request);
    }
}
