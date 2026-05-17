package com.zeromail.core.rules.usecases;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.llm.usecases.LlmGateway;
import com.zeromail.core.llm.usecases.RuleCompileGatewayResult;
import com.zeromail.core.rules.domain.MatcherType;
import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.rules.domain.RuleLanguage;
import com.zeromail.core.rules.domain.RuleSchemaVersion;
import com.zeromail.core.tenant.TenantContext;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Service
public class RuleCompilerService {

    private static final Logger log = LoggerFactory.getLogger(RuleCompilerService.class);
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private final LlmGateway llmGateway;
    private final RuleCompileResultValidator ruleCompileResultValidator;

    public RuleCompilerService(
            LlmGateway llmGateway, RuleCompileResultValidator ruleCompileResultValidator) {
        this.llmGateway = llmGateway;
        this.ruleCompileResultValidator = ruleCompileResultValidator;
    }

    public RuleCompileResult compile(RuleCompileCommand command) {
        RuleLanguage languageHint =
                ruleCompileResultValidator.detectSourceLanguage(command.sourceText());
        CompileMode compileMode = pickInitialCompileMode(command);
        String compilerPayload = buildCompilerPayload(command, languageHint, compileMode);
        log.info("event=rule_compile_started tenantId={}", command.tenantId());

        RuleCompileGatewayResult gatewayResult = callGateway(command, compilerPayload);
        RuleCompileResult compileResult =
                ruleCompileResultValidator.validate(
                        command.sourceText(),
                        gatewayResult.toolName(),
                        gatewayResult.toolArguments());
        if (compileResult.requiresClarification()) {
            RuleCompileResult bestEffortCompileResult =
                    tryResolveClarificationWithoutBlocking(command, languageHint);
            if (bestEffortCompileResult != null) {
                compileResult = bestEffortCompileResult;
            }
        }
        log.info(
                "event=rule_compile_completed tenantId={} status={} reason={}",
                command.tenantId(),
                compileResult.status(),
                compileResult.failureReason());
        return compileResult;
    }

    private RuleCompileGatewayResult callGateway(
            RuleCompileCommand command, String compilerPayload) {
        return callGateway(command, compilerPayload, false);
    }

    private RuleCompileGatewayResult callGateway(
            RuleCompileCommand command, String compilerPayload, boolean reviewDraftRequired) {
        AtomicReference<RuleCompileGatewayResult> gatewayResult = new AtomicReference<>();
        ScopedValue.where(TenantContext.TENANT, command.tenantId().toString())
                .run(
                        () ->
                                gatewayResult.set(
                                        reviewDraftRequired
                                                ? llmGateway.compileRuleReviewDraft(
                                                        CallSite.PREVIEW, compilerPayload)
                                                : llmGateway.compileRule(
                                                        CallSite.PREVIEW, compilerPayload)));
        return gatewayResult.get();
    }

    private static CompileMode pickInitialCompileMode(RuleCompileCommand command) {
        if (command.clarificationAnswer() != null) {
            return CompileMode.AFTER_CLARIFICATION;
        }
        if (command.isRefinement()) {
            return CompileMode.REFINE;
        }
        return CompileMode.INITIAL;
    }

    private static String buildCompilerPayload(
            RuleCompileCommand command, RuleLanguage languageHint, CompileMode compileMode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", RuleSchemaVersion.RULES_V1.id());
        payload.put("compileMode", compileMode.id());
        payload.put("sourceText", command.sourceText());
        payload.put("sourceLanguageHint", languageHint.id());
        payload.put(
                "allowedMatcherIds",
                Arrays.stream(MatcherType.values()).map(MatcherType::id).toList());
        payload.put(
                "allowedActionIds",
                Arrays.stream(RuleActionType.values()).map(RuleActionType::id).toList());
        if (compileMode == CompileMode.AFTER_CLARIFICATION) {
            payload.put("effectiveRuleText", effectiveRuleText(command));
            Map<String, Object> clarification = new LinkedHashMap<>();
            if (command.priorCompileContext() != null) {
                clarification.put("previousQuestion", command.priorCompileContext());
            }
            clarification.put("answer", command.clarificationAnswer());
            payload.put("clarification", clarification);
        }
        if (compileMode == CompileMode.REFINE) {
            payload.put("priorDraft", parsePriorDraftOrRaw(command.priorDraftJson()));
            payload.put("editInstruction", command.editInstruction());
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JacksonException serializationFailure) {
            throw new IllegalArgumentException(
                    "Unable to serialize compiler payload", serializationFailure);
        }
    }

    private static Object parsePriorDraftOrRaw(String priorDraftJson) {
        if (priorDraftJson == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(priorDraftJson);
        } catch (JacksonException malformedPriorDraft) {
            // Fall back to the raw string so the model still sees the draft
            // even if the client serialized it imperfectly. Validation of the
            // returned compiled rule is independent of this input shape.
            return priorDraftJson;
        }
    }

    private RuleCompileResult tryResolveClarificationWithoutBlocking(
            RuleCompileCommand command, RuleLanguage languageHint) {
        if (command.clarificationAnswer() != null) {
            return null;
        }

        log.info("event=rule_compile_force_review_retry tenantId={}", command.tenantId());
        String forceReviewPayload =
                buildCompilerPayload(command, languageHint, CompileMode.FORCE_REVIEW_FORM);
        try {
            RuleCompileGatewayResult forceGatewayResult =
                    callGateway(command, forceReviewPayload, true);
            RuleCompileResult forceCompileResult =
                    ruleCompileResultValidator.validate(
                            command.sourceText(),
                            forceGatewayResult.toolName(),
                            forceGatewayResult.toolArguments());
            if (forceCompileResult.isCompiled()) {
                return forceCompileResult;
            }
        } catch (RuntimeException forceReviewFailure) {
            log.warn(
                    "event=rule_compile_force_review_failed tenantId={}",
                    command.tenantId(),
                    forceReviewFailure);
        }
        // Per project policy (CLAUDE.md: "do not use regex, accent-insensitive
        // keyword matching, substring hacks, or post-hoc string cleanup to
        // infer displayName, matcher.intent, labelName ..."), there is no
        // keyword-extraction fallback here. If the model cannot produce a
        // valid compiled draft across both passes, we return the original
        // clarification instead of fabricating one through regex.
        return null;
    }

    private static String effectiveRuleText(RuleCompileCommand command) {
        if (command.clarificationAnswer() == null) {
            return command.sourceText();
        }
        return command.sourceText() + "\n" + command.clarificationAnswer();
    }

    private enum CompileMode {
        INITIAL("initial"),
        AFTER_CLARIFICATION("after_clarification"),
        FORCE_REVIEW_FORM("force_review_form"),
        REFINE("refine");

        private final String id;

        CompileMode(String id) {
            this.id = id;
        }

        private String id() {
            return id;
        }
    }

    // Keyword-extraction fallback intentionally removed — see CLAUDE.md policy
    // forbidding regex/substring inference of displayName/intent/labelName.
}
