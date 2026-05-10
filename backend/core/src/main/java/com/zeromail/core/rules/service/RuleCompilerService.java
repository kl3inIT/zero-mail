package com.zeromail.core.rules.service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.llm.application.RuleCompileGatewayResult;
import com.zeromail.core.llm.service.LlmGateway;
import com.zeromail.core.rules.domain.MatcherType;
import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.rules.application.RuleCompileCommand;
import com.zeromail.core.rules.application.RuleCompileResult;
import com.zeromail.core.rules.domain.RuleLanguage;
import com.zeromail.core.rules.domain.RuleSchemaVersion;
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
    // Use ScopedValue.where(...).get(Supplier) (Java 25 unchecked variant)
    // so the compiler enforces unchecked-only at the call site. The earlier
    // .call(Callable) variant declares throws Exception, which forced a
    // hand-written rethrow-or-wrap that would silently downgrade any future
    // checked exception added to LlmGateway.compileRule to IllegalStateException
    // and break GlobalExceptionHandler routing.
    return ScopedValue.where(TenantContext.TENANT, command.tenantId().toString())
        .get(() -> llmGateway.compileRule(CallSite.PREVIEW, compilerPayload));
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
