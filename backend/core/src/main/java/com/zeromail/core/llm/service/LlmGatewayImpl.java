package com.zeromail.core.llm.service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.zeromail.core.billing.model.CallSite;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.llm.gateway.sanitization.SanitizationPipeline;
import com.zeromail.core.llm.gateway.springai.ZeroMailLlmProperties;
import com.zeromail.core.llm.model.BYOKProvider;
import com.zeromail.core.llm.model.Action;
import com.zeromail.core.llm.model.LlmChatRequest;
import com.zeromail.core.llm.model.LlmChatResult;
import com.zeromail.core.llm.model.LlmTool;
import com.zeromail.core.llm.model.LlmUsage;
import com.zeromail.core.llm.model.RawToolCall;
import com.zeromail.core.llm.model.SafetyViolationException;
import com.zeromail.core.llm.model.SanitizationContext;
import com.zeromail.core.llm.model.SystemPrompts;
import com.zeromail.core.llm.model.ToolCallResult;
import com.zeromail.core.llm.persistence.TenantByokCredentialsEntity;
import com.zeromail.core.llm.persistence.TenantByokCredentialsRepository;
import com.zeromail.core.tenant.TenantContext;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

@Service
class LlmGatewayImpl implements LlmGateway {

  private static final Logger log = LoggerFactory.getLogger(LlmGatewayImpl.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final LlmModelClient platformLlmModelClient;
  private final SanitizationPipeline sanitizationPipeline;
  private final ZeroMailLlmProperties llmProperties;
  private final AllowListedTools allowListedTools;
  private final ActionValidator actionValidator;
  private final ObservationRegistry observationRegistry;
  private final TenantByokCredentialsRepository tenantByokCredentialsRepository;
  private final RefreshTokenCipher refreshTokenCipher;
  private final ByokLlmModelClient openAiCompatibleByokModelClient;
  private final ByokLlmModelClient anthropicByokModelClient;

  // Plan 06 will wrap platform calls here with CreditLedger reserve/settle/release.

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
        ObservationRegistry.create(),
        null,
        null,
        null,
        null);
  }

  @Autowired
  LlmGatewayImpl(
      LlmModelClient platformLlmModelClient,
      SanitizationPipeline sanitizationPipeline,
      ZeroMailLlmProperties llmProperties,
      AllowListedTools allowListedTools,
      ActionValidator actionValidator,
      ObjectProvider<ObservationRegistry> observationRegistryProvider,
      TenantByokCredentialsRepository tenantByokCredentialsRepository,
      RefreshTokenCipher refreshTokenCipher,
      @Qualifier("openAiCompatibleByokModelClient")
          ByokLlmModelClient openAiCompatibleByokModelClient,
      @Qualifier("anthropicByokModelClient") ByokLlmModelClient anthropicByokModelClient) {
    this(
        platformLlmModelClient,
        sanitizationPipeline,
        llmProperties,
        allowListedTools,
        actionValidator,
        observationRegistryProvider.getIfAvailable(ObservationRegistry::create),
        tenantByokCredentialsRepository,
        refreshTokenCipher,
        openAiCompatibleByokModelClient,
        anthropicByokModelClient);
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
        observationRegistry,
        null,
        null,
        null,
        null);
  }

  private LlmGatewayImpl(
      LlmModelClient platformLlmModelClient,
      SanitizationPipeline sanitizationPipeline,
      ZeroMailLlmProperties llmProperties,
      AllowListedTools allowListedTools,
      ActionValidator actionValidator,
      ObservationRegistry observationRegistry,
      TenantByokCredentialsRepository tenantByokCredentialsRepository,
      RefreshTokenCipher refreshTokenCipher,
      ByokLlmModelClient openAiCompatibleByokModelClient,
      ByokLlmModelClient anthropicByokModelClient) {
    this.platformLlmModelClient = platformLlmModelClient;
    this.sanitizationPipeline = sanitizationPipeline;
    this.llmProperties = llmProperties;
    this.allowListedTools = allowListedTools;
    this.actionValidator = actionValidator;
    this.observationRegistry = observationRegistry;
    this.tenantByokCredentialsRepository = tenantByokCredentialsRepository;
    this.refreshTokenCipher = refreshTokenCipher;
    this.openAiCompatibleByokModelClient = openAiCompatibleByokModelClient;
    this.anthropicByokModelClient = anthropicByokModelClient;
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
              List<LlmTool> tools = allowListedTools.tools();

              try {
                Optional<TenantByokCredentialsEntity> byok = findByokCredentials(tenantId);
                if (byok.isPresent()) {
                  return callViaByokModelClient(byok.get(), sanitizedContext, callSite, tools);
                }
                LlmChatRequest request =
                    new LlmChatRequest(
                        SystemPrompts.TRIAGE_SYSTEM_PROMPT,
                        sanitizedContext.content(),
                        tools,
                        model,
                        0.0,
                        true);
                LlmChatResult result = platformLlmModelClient.call(request);
                ToolCallResult toolCallResult = parseToolCall(result);
                LlmUsage usage = result.usage();
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
                return toolCallResult;
              } catch (SafetyViolationException safetyViolation) {
                log.error(
                    "event=llm_safety_violation tenantId={} callSite={} reason={}",
                    tenantId,
                    callSite,
                    safetyViolation.getClass().getSimpleName());
                throw safetyViolation;
              } catch (RuntimeException callFailure) {
                log.warn(
                    "event=llm_call_failed tenantId={} callSite={} provider={} model={} reason={}",
                    tenantId,
                    callSite,
                    provider,
                    model,
                    callFailure.getClass().getSimpleName());
                throw callFailure;
              }
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
              List<LlmTool> tools = allowListedTools.tools();

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
                ToolCallResult toolCallResult = parseToolCall(result);
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

  private long latencyMs(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }

  private Optional<TenantByokCredentialsEntity> findByokCredentials(UUID tenantId) {
    return tenantByokCredentialsRepository == null
        ? Optional.empty()
        : tenantByokCredentialsRepository.findByTenantId(tenantId);
  }

  private ToolCallResult callViaByokModelClient(
      TenantByokCredentialsEntity byokRow,
      SanitizationContext sanitizedContext,
      CallSite callSite,
      List<LlmTool> tools) {
    UUID tenantId = byokRow.getTenantId();
    String model = llmProperties.modelByCallSite().get(callSite);
    BYOKProvider provider = byokRow.getProvider();
    ByokLlmModelClient byokLlmModelClient =
        switch (provider) {
          case ANTHROPIC -> anthropicByokModelClient;
          case OPENAI_COMPATIBLE -> openAiCompatibleByokModelClient;
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
          new LlmChatRequest(
              SystemPrompts.TRIAGE_SYSTEM_PROMPT,
              sanitizedContext.content(),
              tools,
              model,
              0.0,
              true);
      LlmChatResult result = byokLlmModelClient.call(decryptedKey, byokRow.getEndpoint(), request);
      ToolCallResult toolCallResult = parseToolCall(result);
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
      return toolCallResult;
    } finally {
      Arrays.fill(decryptedKey, (byte) 0);
    }
  }

  private ToolCallResult parseToolCall(LlmChatResult result) {
    if (result.toolCalls() == null || result.toolCalls().isEmpty()) {
      throw new SafetyViolationException();
    }
    RawToolCall rawToolCall = result.toolCalls().getFirst();
    Action action = actionValidator.validate(rawToolCall.functionName());
    return new ToolCallResult(action, parseJsonArgs(rawToolCall.argsJson()));
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
}
