package com.zeromail.core.llm.gateway.springai;

import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.ai.model.ApiKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.zeromail.core.config.ZeroMailCoreProperties;

import org.jspecify.annotations.NonNull;

@Component
class PlatformApiKey implements ApiKey {

  private final Supplier<String> apiKeySupplier;

  @Autowired
  PlatformApiKey(ZeroMailCoreProperties zeroMailCoreProperties) {
    this(() -> zeroMailCoreProperties.llm().platform().apiKey());
  }

  PlatformApiKey(Supplier<String> apiKeySupplier) {
    this.apiKeySupplier = Objects.requireNonNull(apiKeySupplier, "apiKeySupplier");
  }

  @Override
  public @NonNull String getValue() {
    return apiKeySupplier.get();
  }
}
