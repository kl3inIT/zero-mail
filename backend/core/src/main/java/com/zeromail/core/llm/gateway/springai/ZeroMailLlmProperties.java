package com.zeromail.core.llm.gateway.springai;

import java.util.Map;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.zeromail.core.billing.model.CallSite;
import com.zeromail.core.llm.model.BYOKProvider;

@ConfigurationProperties("zero-mail.llm.platform")
public record ZeroMailLlmProperties(
    BYOKProvider provider,
    String baseUrl,
    String apiKey,
    String compileModel,
    String driftModel,
    String triageModel,
    java.time.Duration connectTimeout,
    java.time.Duration readTimeout) {

  public ZeroMailLlmProperties {
    provider = provider == null ? BYOKProvider.OPENAI_COMPATIBLE : provider;
    baseUrl = baseUrl == null ? "https://openrouter.ai/api/v1" : baseUrl;
    compileModel = compileModel == null ? "openai/gpt-4o-mini" : compileModel;
    driftModel = driftModel == null ? "openai/gpt-4o-mini" : driftModel;
    triageModel = triageModel == null ? "openai/gpt-4o-mini" : triageModel;
    connectTimeout = connectTimeout == null ? java.time.Duration.ofSeconds(5) : connectTimeout;
    readTimeout = readTimeout == null ? java.time.Duration.ofSeconds(30) : readTimeout;
    Objects.requireNonNull(apiKey, "zero-mail.llm.platform.api-key");
  }

  public Map<CallSite, String> modelByCallSite() {
    return Map.of(
        CallSite.TRIAGE, triageModel,
        CallSite.DRAFT, compileModel,
        CallSite.PREVIEW, compileModel);
  }
}
