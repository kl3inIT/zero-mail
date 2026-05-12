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
        String compilerPayload = buildCompilerPayload(command, languageHint);
        log.info("event=rule_compile_started tenantId={}", command.tenantId());

        RuleCompileGatewayResult gatewayResult = callGateway(command, compilerPayload);
        RuleCompileResult compileResult =
                ruleCompileResultValidator.validate(
                        command.sourceText(),
                        gatewayResult.toolName(),
                        gatewayResult.toolArguments());
        log.info(
                "event=rule_compile_completed tenantId={} status={} reason={}",
                command.tenantId(),
                compileResult.status(),
                compileResult.failureReason());
        return compileResult;
    }

    private RuleCompileGatewayResult callGateway(
            RuleCompileCommand command, String compilerPayload) {
        AtomicReference<RuleCompileGatewayResult> gatewayResult = new AtomicReference<>();
        ScopedValue.where(TenantContext.TENANT, command.tenantId().toString())
                .run(
                        () ->
                                gatewayResult.set(
                                        llmGateway.compileRule(CallSite.PREVIEW, compilerPayload)));
        return gatewayResult.get();
    }

    private static String buildCompilerPayload(
            RuleCompileCommand command, RuleLanguage languageHint) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", RuleSchemaVersion.RULES_V1.id());
        payload.put("sourceText", command.sourceText());
        payload.put("sourceLanguageHint", languageHint.id());
        payload.put(
                "allowedMatcherIds",
                Arrays.stream(MatcherType.values()).map(MatcherType::id).toList());
        payload.put(
                "allowedActionIds",
                Arrays.stream(RuleActionType.values()).map(RuleActionType::id).toList());
        if (command.clarificationAnswer() != null) {
            payload.put("clarificationAnswer", command.clarificationAnswer());
        }
        if (command.priorCompileContext() != null) {
            payload.put("priorCompileContext", command.priorCompileContext());
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JacksonException serializationFailure) {
            throw new IllegalArgumentException(
                    "Unable to serialize compiler payload", serializationFailure);
        }
    }
}
