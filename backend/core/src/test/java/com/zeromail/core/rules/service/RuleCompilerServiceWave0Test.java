package com.zeromail.core.rules.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.llm.service.LlmGateway;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class RuleCompilerServiceWave0Test {

    private static final String PLAN_03_02_GATEWAY_MESSAGE =
            "Plan 03-02 lands LlmGateway.compileRule and rule compile gateway result symbols";
    private static final String PLAN_03_03_COMPILER_MESSAGE =
            "Plan 03-03 lands RuleCompilerService validation and ambiguity behavior";

    @Test
    @Disabled(PLAN_03_02_GATEWAY_MESSAGE)
    void compiler_uses_preview_call_site_on_the_gateway_owned_compile_method() throws Exception {
        Class<?> compilerPayloadClass =
                Class.forName("com.zeromail.core.llm.usecases.RuleCompilerPayload");
        Method compileRuleMethod =
                LlmGateway.class.getMethod("compileRule", CallSite.class, compilerPayloadClass);

        assertThat(compileRuleMethod).isNotNull();
        assertThat(CallSite.PREVIEW.id()).isEqualTo("PREVIEW");
    }

    @Test
    @Disabled(PLAN_03_03_COMPILER_MESSAGE)
    void compiler_rejects_unknown_tool_name_and_persists_no_rule() throws Exception {
        Object compilerService = newFutureCompilerServiceWithRepositoryProbe();
        Method compileMethod =
                compilerService
                        .getClass()
                        .getMethod(
                                "compile",
                                Class.forName(
                                        "com.zeromail.core.rules.usecases.RuleCompileCommand"));

        assertThatThrownBy(() -> compileMethod.invoke(compilerService, unknownToolCommand()))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThat(ruleRepositorySaveCount(compilerService)).isZero();
    }

    @Test
    @Disabled(PLAN_03_03_COMPILER_MESSAGE)
    void compiler_rejects_unknown_matcher_and_action_arguments_before_persistence()
            throws Exception {
        Class<?> validatorClass =
                Class.forName("com.zeromail.core.rules.service.RuleCompileResultValidator");
        Object validator = validatorClass.getConstructor().newInstance();
        Method validateMethod =
                validatorClass.getMethod("validate", String.class, String.class, Map.class);

        assertThatThrownBy(
                        () ->
                                validateMethod.invoke(
                                        validator,
                                        "Archive receipts",
                                        "rule_compile",
                                        Map.of(
                                                "matcher",
                                                Map.of("type", "EVERYTHING"),
                                                "actions",
                                                java.util.List.of(Map.of("type", "send")))))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Disabled(PLAN_03_03_COMPILER_MESSAGE)
    void ambiguous_compile_returns_one_clarification_and_persists_nothing() throws Exception {
        Class<?> clarificationClass =
                Class.forName("com.zeromail.core.rules.usecases.RuleCompileClarification");
        Object compilerService = newFutureCompilerServiceWithRepositoryProbe();
        Method compileMethod =
                compilerService
                        .getClass()
                        .getMethod(
                                "compile",
                                Class.forName(
                                        "com.zeromail.core.rules.usecases.RuleCompileCommand"));

        Object compileResult = compileMethod.invoke(compilerService, ambiguousCommand());
        Method clarificationMethod = compileResult.getClass().getMethod("clarification");

        assertThat(clarificationMethod.invoke(compileResult)).isInstanceOf(clarificationClass);
        assertThat(ruleRepositorySaveCount(compilerService)).isZero();
    }

    private static Object newFutureCompilerServiceWithRepositoryProbe() throws Exception {
        return Class.forName("com.zeromail.core.rules.service.RuleCompilerService")
                .getConstructor()
                .newInstance();
    }

    private static Object unknownToolCommand() {
        return Map.of("sourceText", "Run a webhook for all receipts");
    }

    private static Object ambiguousCommand() {
        return Map.of("sourceText", "Clean up newsletters");
    }

    private static int ruleRepositorySaveCount(Object compilerService) throws Exception {
        Method savedRuleCountMethod = compilerService.getClass().getMethod("savedRuleCountForTest");
        return (Integer) savedRuleCountMethod.invoke(compilerService);
    }
}
