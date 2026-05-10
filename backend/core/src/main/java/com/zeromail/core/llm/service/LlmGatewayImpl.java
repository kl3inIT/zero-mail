package com.zeromail.core.llm.service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.zeromail.core.billing.exception.InsufficientCreditsException;
import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.billing.domain.ReservationId;
import com.zeromail.core.billing.service.CreditLedger;
import com.zeromail.core.config.ZeroMailCoreProperties;
import com.zeromail.core.config.ZeroMailCoreProperties.ZeroMailLlmProperties;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.llm.gateway.sanitization.SanitizationPipeline;
import com.zeromail.core.llm.domain.BYOKProvider;
import com.zeromail.core.llm.domain.Action;
import com.zeromail.core.llm.application.LlmChatRequest;
import com.zeromail.core.llm.application.LlmChatResult;
import com.zeromail.core.llm.application.LlmTool;
import com.zeromail.core.llm.domain.LlmToolProfile;
import com.zeromail.core.llm.application.LlmUsage;
import com.zeromail.core.llm.application.RawToolCall;
import com.zeromail.core.llm.application.RuleCompileGatewayResult;
import com.zeromail.core.llm.exception.SafetyViolationException;
import com.zeromail.core.llm.application.SanitizationContext;
import com.zeromail.core.llm.application.SystemPrompts;
import com.zeromail.core.llm.application.ToolCallResult;
import com.zeromail.core.llm.persistence.TenantByokCredentialsEntity;
import com.zeromail.core.llm.persistence.TenantByokCredentialsRepository;
import com.zeromail.core.tenant.TenantContext;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@Service
class LlmGatewayImpl implements LlmGateway {

  private static final Logger log = LoggerFactory.getLogger(LlmGatewayImpl.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final CreditLedger NOOP_CREDIT_LEDGER = new NoopCreditLedger();
  private static final String DEFAULT_ANTHROPIC_BYOK_MODEL = "claude-3-haiku-20240307";

  private final LlmModelClient platformLlmModelClient;
  private final SanitizationPipeline sanitizationPipeline;
  private final ZeroMailLlmProperties llmProperties;
  private final AllowListedTools allowListedTools;
  private final ActionValidator actionValidator;
  private final RuleCompileToolValidator ruleCompileToolValidator;
  private final ObservationRegistry observationRegistry;
  private final TenantByokCredentialsRepository tenantByokCredentialsRepository;
  private final RefreshTokenCipher refreshTokenCipher;
  private final ByokLlmModelClient openAiByokModelClient;
  private final ByokLlmModelClient anthropicByokModelClient;
  private final ByokLlmModelClient googleGenAiByokModelClient;
  private final ByokLlmModelClient deepSeekByokModelClient;
  private final CreditLedger creditLedger;
  private final MeterRegistry meterRegistry;

  LlmGatewayImpl(
      LlmModelClient platformLlmModelClient,
      SanitizationPipeline sanitizationPipeline,
      ZeroMailLlmProperties llmProperties,
      AllowListedTools allowListedTools,
      ActionValidator actionValidator) {
    this(
        platformLlmModelClient,
        sanitizationPipeline,
        llmProperties,
        allowListedTools,
        actionValidator,
        new RuleCompileToolValidator(),
        ObservationRegistry.create(),
        null,
        null,
        null,
        null,
        null,
        null,
        NOOP_CREDIT_LEDGER,
        new SimpleMeterRegistry());
  }

  @Autowired
  LlmGatewayImpl(
      LlmModelClient platformLlmModelClient,
      SanitizationPipeline sanitizationPipeline,
      ZeroMailCoreProperties zeroMailCoreProperties,
      AllowListedTools allowListedTools,
      ActionValidator actionValidator,
      RuleCompileToolValidator ruleCompileToolValidator,
      ObjectProvider<ObservationRegistry> observationRegistryProvider,
      TenantByokCredentialsRepository tenantByokCredentialsRepository,
      RefreshTokenCipher refreshTokenCipher,
      @Qualifier("openAiByokModelClient") ByokLlmModelClient openAiByokModelClient,
      @Qualifier("anthropicByokModelClient") ByokLlmModelClient anthropicByokModelClient,
      @Qualifier("googleGenAiByokModelClient") ByokLlmModelClient googleGenAiByokModelClient,
      @Qualifier("deepSeekByokModelClient") ByokLlmModelClient deepSeekByokModelClient,
      CreditLedger creditLedger,
      ObjectProvider<MeterRegistry> meterRegistryProvider) {
    this(
        platformLlmModelClient,
        sanitizationPipeline,
        zeroMailCoreProperties.llm().platform(),
        allowListedTools,
        actionValidator,
        ruleCompileToolValidator,
        observationRegistryProvider.getIfAvailable(ObservationRegistry::create),
        tenantByokCredentialsRepository,
        refreshTokenCipher,
        openAiByokModelClient,
        anthropicByokModelClient,
        googleGenAiByokModelClient,
        deepSeekByokModelClient,
        creditLedger,
        meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new));
  }

  LlmGatewayImpl(
      LlmModelClient platformLlmModelClient,
      SanitizationPipeline sanitizationPipeline,
      ZeroMailLlmProperties llmProperties,
      AllowListedTools allowListedTools,
      ActionValidator actionValidator,
      ObservationRegistry observationRegistry) {
    this(
        platformLlmModelClient,
        sanitizationPipeline,
        llmProperties,
        allowListedTools,
        actionValidator,
        new RuleCompileToolValidator(),
        observationRegistry,
        null,
        null,
        null,
        null,
        null,
        null,
        NOOP_CREDIT_LEDGER,
        new SimpleMeterRegistry());
  }

  private LlmGatewayImpl(
      LlmModelClient platformLlmModelClient,
      SanitizationPipeline sanitizationPipeline,
      ZeroMailLlmProperties llmProperties,
      AllowListedTools allowListedTools,
      ActionValidator actionValidator,
      RuleCompileToolValidator ruleCompileToolValidator,
      ObservationRegistry observationRegistry,
      TenantByokCredentialsRepository tenantByokCredentialsRepository,
      RefreshTokenCipher refreshTokenCipher,
      ByokLlmModelClient openAiByokModelClient,
      ByokLlmModelClient anthropicByokModelClient,
      ByokLlmModelClient googleGenAiByokModelClient,
      ByokLlmModelClient deepSeekByokModelClient,
      CreditLedger creditLedger,
      MeterRegistry meterRegistry) {
    this.platformLlmModelClient = platformLlmModelClient;
    this.sanitizationPipeline = sanitizationPipeline;
    this.llmProperties = llmProperties;
    this.allowListedTools = allowListedTools;
    this.actionValidator = actionValidator;
    this.ruleCompileToolValidator = ruleCompileToolValidator;
    this.observationRegistry = observationRegistry;
    this.tenantByokCredentialsRepository = tenantByokCredentialsRepository;
    this.refreshTokenCipher = refreshTokenCipher;
    this.openAiByokModelClient = openAiByokModelClient;
    this.anthropicByokModelClient = anthropicByokModelClient;
    this.googleGenAiByokModelClient = googleGenAiByokModelClient;
    this.deepSeekByokModelClient = deepSeekByokModelClient;
    this.creditLedger = creditLedger;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public ToolCallResult chat(CallSite callSite, String rawHtml) {
    UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
    String model = llmProperties.modelByCallSite().get(callSite);
    String provider = llmProperties.provider().id();
    long startNanos = System.nanoTime();
    return Observation.createNotStarted("zero_mail.llm.gateway", observationRegistry)
        .lowCardinalityKeyValue("tenantId", tenantId.toString())
        .lowCardinalityKeyValue("callSite", callSite.id())
        .lowCardinalityKeyValue("provider", provider)
        .lowCardinalityKeyValue("model", model)
        .observe(
            () -> {
              log.info(
                  "event=llm_call_started tenantId={} callSite={} provider={} model={}",
                  tenantId,
                  callSite,
                  provider,
                  model);

              SanitizationContext sanitizedContext = sanitizationPipeline.sanitize(rawHtml);
              List<LlmTool> tools = allowListedTools.tools(LlmToolProfile.SAFE_ACTIONS);

              Optional<TenantByokCredentialsEntity> byok = findByokCredentials(tenantId);
              if (byok.isPresent()) {
                // LLM-04 — BYOK skips credit ledger by design.
                return callViaByokModelClient(
                    byok.get(),
                    sanitizedContext,
                    callSite,
                    SystemPrompts.TRIAGE_SYSTEM_PROMPT,
                    tools,
                    (_, result) -> parseSafeActionToolCall(result));
              }

              return callPlatformModelClientWithCreditLedger(
                  tenantId,
                  callSite,
                  provider,
                  model,
                  sanitizedContext,
                  SystemPrompts.TRIAGE_SYSTEM_PROMPT,
                  tools,
                  startNanos,
                  (_, result) -> parseSafeActionToolCall(result));
            });
  }

  @Override
  public RuleCompileGatewayResult compileRule(CallSite callSite, String compilerPayload) {
    if (callSite != CallSite.PREVIEW) {
      throw new IllegalArgumentException("Rule compilation is only supported for PREVIEW");
    }

    UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
    String model = llmProperties.modelByCallSite().get(callSite);
    String provider = llmProperties.provider().id();
    long startNanos = System.nanoTime();
    return Observation.createNotStarted("zero_mail.llm.gateway.rule_compile", observationRegistry)
        .lowCardinalityKeyValue("tenantId", tenantId.toString())
        .lowCardinalityKeyValue("callSite", callSite.id())
        .lowCardinalityKeyValue("provider", provider)
        .lowCardinalityKeyValue("model", model)
        .observe(
            () -> {
              log.info(
                  "event=llm_rule_compile_started tenantId={} callSite={} provider={} model={}",
                  tenantId,
                  callSite,
                  provider,
                  model);

              SanitizationContext sanitizedContext = sanitizationPipeline.sanitize(compilerPayload);
              List<LlmTool> tools = allowListedTools.tools(LlmToolProfile.RULE_COMPILE);

              Optional<TenantByokCredentialsEntity> byok = findByokCredentials(tenantId);
              if (byok.isPresent()) {
                return callViaByokModelClient(
                    byok.get(),
                    sanitizedContext,
                    callSite,
                    SystemPrompts.RULE_COMPILE_SYSTEM_PROMPT,
                    tools,
                    this::parseRuleCompileToolCall);
              }

              return callPlatformModelClientWithCreditLedger(
                  tenantId,
                  callSite,
                  provider,
                  model,
                  sanitizedContext,
                  SystemPrompts.RULE_COMPILE_SYSTEM_PROMPT,
                  tools,
                  startNanos,
                  this::parseRuleCompileToolCall);
            });
  }

  @Override
  public ToolCallResult driftCheck(String rawEmailFixture) {
    UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
    String model = llmProperties.driftModel();
    String provider = llmProperties.provider().id();
    long startNanos = System.nanoTime();
    return Observation.createNotStarted("zero_mail.llm.gateway.drift", observationRegistry)
        .lowCardinalityKeyValue("tenantId", tenantId.toString())
        .lowCardinalityKeyValue("provider", provider)
        .lowCardinalityKeyValue("model", model)
        .observe(
            () -> {
              log.info(
                  "event=llm_drift_call_started tenantId={} provider={} model={}",
                  tenantId,
                  provider,
                  model);

              SanitizationContext sanitizedContext = sanitizationPipeline.sanitize(rawEmailFixture);
              List<LlmTool> tools = allowListedTools.tools(LlmToolProfile.SAFE_ACTIONS);

              // D-E3 — drift is a platform-cost operation, not user-billable; ledger NOT touched.
              try {
                LlmChatRequest request =
                    new LlmChatRequest(
                        SystemPrompts.TRIAGE_SYSTEM_PROMPT,
                        sanitizedContext.content(),
                        tools,
                        model,
                        0.0,
                        true);
                LlmChatResult result = platformLlmModelClient.call(request);
                ToolCallResult toolCallResult = parseSafeActionToolCall(result);
                log.info(
                    "event=llm_drift_call_succeeded tenantId={} provider={} model={} latencyMs={} "
                        + "truncated={}",
                    tenantId,
                    provider,
                    model,
                    latencyMs(startNanos),
                    sanitizedContext.truncated());
                return toolCallResult;
              } catch (SafetyViolationException safetyViolation) {
                log.error(
                    "event=llm_safety_violation tenantId={} callSite=DRIFT reason={}",
                    tenantId,
                    safetyViolation.getClass().getSimpleName());
                throw safetyViolation;
              } catch (RuntimeException driftFailure) {
                log.warn(
                    "event=llm_drift_call_failed tenantId={} provider={} model={} reason={}",
                    tenantId,
                    provider,
                    model,
                    driftFailure.getClass().getSimpleName());
                throw driftFailure;
              }
            });
  }

  private <T> T callPlatformModelClientWithCreditLedger(
      UUID tenantId,
      CallSite callSite,
      String provider,
      String model,
      SanitizationContext sanitizedContext,
      String systemPrompt,
      List<LlmTool> tools,
      long startNanos,
      BiFunction<String, LlmChatResult, T> resultParser) {
    ReservationId reservationId;
    try {
      reservationId = creditLedger.reserve(tenantId, callSite);
    } catch (InsufficientCreditsException insufficientCreditsException) {
      log.warn(
          "event=llm_call_blocked_insufficient_credits tenantId={} callSite={}",
          tenantId,
          callSite);
      throw insufficientCreditsException;
    }

    T gatewayResult;
    LlmUsage usage;
    try {
      LlmChatRequest request =
          new LlmChatRequest(systemPrompt, sanitizedContext.content(), tools, model, 0.0, true);
      LlmChatResult result = platformLlmModelClient.call(request);
      gatewayResult = resultParser.apply(model, result);
      usage = result.usage();
      log.info(
          "event=llm_call_succeeded tenantId={} callSite={} provider={} model={} latencyMs={} "
              + "promptTokens={} completionTokens={} stopReason={} truncated={}",
          tenantId,
          callSite,
          provider,
          model,
          latencyMs(startNanos),
          usage.promptTokens(),
          usage.completionTokens(),
          usage.finishReason(),
          sanitizedContext.truncated());
    } catch (SafetyViolationException safetyViolation) {
      creditLedger.release(reservationId);
      meterRegistry
          .counter("llm_safety_violation_cost_absorbed_total", "tenantId", tenantId.toString())
          .increment();
      log.error(
          "event=llm_safety_violation tenantId={} callSite={} reason={}",
          tenantId,
          callSite,
          safetyViolation.getClass().getSimpleName());
      throw safetyViolation;
    } catch (RuntimeException callFailure) {
      creditLedger.release(reservationId);
      log.warn(
          "event=llm_call_failed tenantId={} callSite={} provider={} model={} reason={}",
          tenantId,
          callSite,
          provider,
          model,
          callFailure.getClass().getSimpleName());
      throw callFailure;
    }

    try {
      creditLedger.settle(reservationId);
    } catch (RuntimeException settleFailure) {
      log.error(
          "event=llm_call_settle_failed tenantId={} callSite={} reason={}",
          tenantId,
          callSite,
          settleFailure.getClass().getSimpleName());
      throw settleFailure;
    }
    return gatewayResult;
  }

  private long latencyMs(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }

  private Optional<TenantByokCredentialsEntity> findByokCredentials(UUID tenantId) {
    return tenantByokCredentialsRepository == null
        ? Optional.empty()
        : tenantByokCredentialsRepository.findByTenantId(tenantId);
  }

  private <T> T callViaByokModelClient(
      TenantByokCredentialsEntity byokRow,
      SanitizationContext sanitizedContext,
      CallSite callSite,
      String systemPrompt,
      List<LlmTool> tools,
      BiFunction<String, LlmChatResult, T> resultParser) {
    UUID tenantId = byokRow.getTenantId();
    BYOKProvider provider = byokRow.getProvider();
    String model = modelForByok(provider, byokRow.getModel(), callSite);
    ByokLlmModelClient byokLlmModelClient =
        switch (provider) {
          case ANTHROPIC -> anthropicByokModelClient;
          case DEEPSEEK -> deepSeekByokModelClient;
          case GOOGLE_GENAI -> googleGenAiByokModelClient;
          case OPENAI -> openAiByokModelClient;
        };
    byte[] decryptedKey =
        refreshTokenCipher.decrypt(byokRow.getEncryptedKey(), tenantId.toString());
    try {
      long startNanos = System.nanoTime();
      log.info(
          "event=llm_byok_call_started tenantId={} provider={} model={}",
          tenantId,
          provider,
          model);
      LlmChatRequest request =
          new LlmChatRequest(systemPrompt, sanitizedContext.content(), tools, model, 0.0, true);
      LlmChatResult result = byokLlmModelClient.call(decryptedKey, byokRow.getEndpoint(), request);
      T gatewayResult = resultParser.apply(model, result);
      LlmUsage usage = result.usage();
      log.info(
          "event=llm_byok_call_succeeded tenantId={} provider={} model={} latencyMs={} "
              + "promptTokens={} completionTokens={} stopReason={} truncated={}",
          tenantId,
          provider,
          model,
          latencyMs(startNanos),
          usage.promptTokens(),
          usage.completionTokens(),
          usage.finishReason(),
          sanitizedContext.truncated());
      return gatewayResult;
    } finally {
      Arrays.fill(decryptedKey, (byte) 0);
    }
  }

  private ToolCallResult parseSafeActionToolCall(LlmChatResult result) {
    if (result.toolCalls() == null || result.toolCalls().isEmpty()) {
      throw new SafetyViolationException();
    }
    RawToolCall rawToolCall = result.toolCalls().getFirst();
    Action action = actionValidator.validate(rawToolCall.functionName());
    return new ToolCallResult(action, parseJsonArgs(rawToolCall.argsJson()));
  }

  private RuleCompileGatewayResult parseRuleCompileToolCall(String model, LlmChatResult result) {
    if (result.toolCalls() == null || result.toolCalls().isEmpty()) {
      throw new SafetyViolationException();
    }
    RawToolCall rawToolCall = result.toolCalls().getFirst();
    String toolName = ruleCompileToolValidator.validate(rawToolCall.functionName());
    return new RuleCompileGatewayResult(toolName, model, parseJsonArgs(rawToolCall.argsJson()));
  }

  private Map<String, Object> parseJsonArgs(String argumentsJson) {
    try {
      Object parsedArguments = OBJECT_MAPPER.readValue(argumentsJson, Object.class);
      if (!(parsedArguments instanceof Map<?, ?> parsedArgumentsMap)) {
        throw new IllegalStateException("Tool call arguments must be a JSON object");
      }
      Map<String, Object> arguments = new LinkedHashMap<>();
      for (Map.Entry<?, ?> argumentEntry : parsedArgumentsMap.entrySet()) {
        if (argumentEntry.getKey() instanceof String argumentName) {
          arguments.put(argumentName, argumentEntry.getValue());
        }
      }
      return arguments;
    } catch (JacksonException jsonParsingFailure) {
      throw new IllegalStateException("Unable to parse tool call arguments", jsonParsingFailure);
    }
  }

  private String modelForByok(BYOKProvider provider, String storedModel, CallSite callSite) {
    if (storedModel != null && !storedModel.isBlank()) {
      return storedModel.trim();
    }
    return switch (provider) {
      case ANTHROPIC -> DEFAULT_ANTHROPIC_BYOK_MODEL;
      case DEEPSEEK -> "deepseek-chat";
      case GOOGLE_GENAI -> "gemini-2.0-flash";
      case OPENAI -> llmProperties.modelByCallSite().get(callSite);
    };
  }

  private static final class NoopCreditLedger implements CreditLedger {

    private static final ReservationId RESERVATION_ID =
        new ReservationId(UUID.fromString("00000000-0000-0000-0000-000000000000"));

    @Override
    public ReservationId reserve(UUID tenantId, CallSite callSite) {
      return RESERVATION_ID;
    }

    @Override
    public void settle(ReservationId reservationId) {}

    @Override
    public void release(ReservationId reservationId) {}

    @Override
    public com.zeromail.core.billing.domain.CreditBalance balance(UUID tenantId) {
      return new com.zeromail.core.billing.domain.CreditBalance(0, 0);
    }
  }
}
