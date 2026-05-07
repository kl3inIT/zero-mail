package com.zeromail.core.llm.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.zeromail.core.billing.model.CallSite;
import com.zeromail.core.llm.gateway.sanitization.SanitizationPipeline;
import com.zeromail.core.llm.gateway.springai.ZeroMailLlmProperties;
import com.zeromail.core.llm.model.Action;
import com.zeromail.core.llm.model.LlmChatRequest;
import com.zeromail.core.llm.model.LlmChatResult;
import com.zeromail.core.llm.model.LlmTool;
import com.zeromail.core.llm.model.LlmUsage;
import com.zeromail.core.llm.model.RawToolCall;
import com.zeromail.core.llm.model.SanitizationContext;
import com.zeromail.core.llm.model.SystemPrompts;
import com.zeromail.core.llm.model.ToolCallResult;
import com.zeromail.core.tenant.TenantContext;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
class LlmGatewayImpl implements LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(LlmGatewayImpl.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LlmModelClient platformLlmModelClient;
    private final SanitizationPipeline sanitizationPipeline;
    private final ZeroMailLlmProperties llmProperties;
    private final AllowListedTools allowListedTools;
    // Plan 04 will add ActionValidator-backed tool-call validation at parseToolCall(...).
    // Plan 05 will branch here before platform calls to route tenant BYOK credentials.
    // Plan 06 will wrap platform calls here with CreditLedger reserve/settle/release.

    LlmGatewayImpl(
            LlmModelClient platformLlmModelClient,
            SanitizationPipeline sanitizationPipeline,
            ZeroMailLlmProperties llmProperties,
            AllowListedTools allowListedTools) {
        this.platformLlmModelClient = platformLlmModelClient;
        this.sanitizationPipeline = sanitizationPipeline;
        this.llmProperties = llmProperties;
        this.allowListedTools = allowListedTools;
    }

    @Override
    public ToolCallResult chat(CallSite callSite, String rawHtml) {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        String model = llmProperties.modelByCallSite().get(callSite);
        String provider = llmProperties.provider().id();
        long startNanos = System.nanoTime();
        log.info("event=llm_call_started tenantId={} callSite={} provider={} model={}",
                tenantId, callSite, provider, model);

        SanitizationContext sanitizedContext = sanitizationPipeline.sanitize(rawHtml);
        List<LlmTool> tools = allowListedTools.tools();

        try {
            LlmChatRequest request = new LlmChatRequest(
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
        } catch (RuntimeException callFailure) {
            log.warn("event=llm_call_failed tenantId={} callSite={} provider={} model={} reason={}",
                    tenantId, callSite, provider, model, callFailure.getClass().getSimpleName());
            throw callFailure;
        }
    }

    @Override
    public ToolCallResult driftCheck(String rawEmailFixture) {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        String model = llmProperties.driftModel();
        String provider = llmProperties.provider().id();
        long startNanos = System.nanoTime();
        log.info("event=llm_drift_call_started tenantId={} provider={} model={}", tenantId, provider, model);

        SanitizationContext sanitizedContext = sanitizationPipeline.sanitize(rawEmailFixture);
        List<LlmTool> tools = allowListedTools.tools();

        try {
            LlmChatRequest request = new LlmChatRequest(
                    SystemPrompts.TRIAGE_SYSTEM_PROMPT,
                    sanitizedContext.content(),
                    tools,
                    model,
                    0.0,
                    true);
            LlmChatResult result = platformLlmModelClient.call(request);
            ToolCallResult toolCallResult = parseToolCall(result);
            log.info(
                    "event=llm_drift_call_succeeded tenantId={} provider={} model={} latencyMs={} truncated={}",
                    tenantId,
                    provider,
                    model,
                    latencyMs(startNanos),
                    sanitizedContext.truncated());
            return toolCallResult;
        } catch (RuntimeException driftFailure) {
            log.warn("event=llm_drift_call_failed tenantId={} provider={} model={} reason={}",
                    tenantId, provider, model, driftFailure.getClass().getSimpleName());
            throw driftFailure;
        }
    }

    private long latencyMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private ToolCallResult parseToolCall(LlmChatResult result) {
        // Plan 04 replaces this minimal parser with ActionValidator-backed fail-closed validation.
        if (result.toolCalls().isEmpty()) {
            throw new IllegalStateException("No tool call returned");
        }
        RawToolCall rawToolCall = result.toolCalls().getFirst();
        Action action = Action.fromFunctionName(rawToolCall.functionName());
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
