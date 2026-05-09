package com.zeromail.core.rules.service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.zeromail.core.billing.model.CallSite;
import com.zeromail.core.llm.model.RuleCompileGatewayResult;
import com.zeromail.core.llm.service.LlmGateway;
import com.zeromail.core.rules.model.MatcherType;
import com.zeromail.core.rules.model.RuleActionType;
import com.zeromail.core.rules.model.RuleCompileCommand;
import com.zeromail.core.rules.model.RuleCompileResult;
import com.zeromail.core.rules.model.RuleLanguage;
import com.zeromail.core.rules.model.RuleSchemaVersion;
import com.zeromail.core.tenant.TenantContext;

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
    RuleLanguage languageHint = ruleCompileResultValidator.detectSourceLanguage(command.sourceText());
    String compilerPayload = buildCompilerPayload(command, languageHint);
    log.info("event=rule_compile_started tenantId={}", command.tenantId());

    RuleCompileGatewayResult gatewayResult = callGateway(command, compilerPayload);
    RuleCompileResult compileResult =
        ruleCompileResultValidator.validate(
            command.sourceText(), gatewayResult.toolName(), gatewayResult.toolArguments());
    log.info(
        "event=rule_compile_completed tenantId={} status={} reason={}",
        command.tenantId(),
        compileResult.status(),
        compileResult.failureReason());
    return compileResult;
  }

  private RuleCompileGatewayResult callGateway(RuleCompileCommand command, String compilerPayload) {
    try {
      return ScopedValue.where(TenantContext.TENANT, command.tenantId().toString())
          .call(() -> llmGateway.compileRule(CallSite.PREVIEW, compilerPayload));
    } catch (RuntimeException runtimeFailure) {
      throw runtimeFailure;
    } catch (Exception checkedFailure) {
      throw new IllegalStateException("Rule compile gateway failed", checkedFailure);
    }
  }

  private static String buildCompilerPayload(RuleCompileCommand command, RuleLanguage languageHint) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("schemaVersion", RuleSchemaVersion.RULES_V1.id());
    payload.put("sourceText", command.sourceText());
    payload.put("sourceLanguageHint", languageHint.id());
    payload.put(
        "allowedMatcherIds", Arrays.stream(MatcherType.values()).map(MatcherType::id).toList());
    payload.put(
        "allowedActionIds", Arrays.stream(RuleActionType.values()).map(RuleActionType::id).toList());
    if (command.clarificationAnswer() != null) {
      payload.put("clarificationAnswer", command.clarificationAnswer());
    }
    if (command.priorCompileContext() != null) {
      payload.put("priorCompileContext", command.priorCompileContext());
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(payload);
    } catch (JacksonException serializationFailure) {
      throw new IllegalArgumentException("Unable to serialize compiler payload", serializationFailure);
    }
  }
}
