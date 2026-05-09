package com.zeromail.core.llm.model;

import java.util.NoSuchElementException;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.zeromail.core.shared.lang.IdentifiedEnum;

public enum ByokProviderPreset implements IdentifiedEnum {

  OPENROUTER("openrouter", BYOKProvider.OPENAI, "https://openrouter.ai/api/v1", false),
  OPENAI("openai", BYOKProvider.OPENAI, "https://api.openai.com/v1", false),
  ANTHROPIC("anthropic", BYOKProvider.ANTHROPIC, "https://api.anthropic.com/v1", false),
  GOOGLE_GENAI(
      "google-genai",
      BYOKProvider.GOOGLE_GENAI,
      "https://generativelanguage.googleapis.com/v1beta",
      false),
  DEEPSEEK("deepseek", BYOKProvider.DEEPSEEK, "https://api.deepseek.com", false),
  OPENAI_COMPATIBLE("openai-compatible", BYOKProvider.OPENAI, null, true),
  ANTHROPIC_COMPATIBLE("anthropic-compatible", BYOKProvider.ANTHROPIC, null, true);

  private final String id;
  private final BYOKProvider provider;
  private final String fixedEndpoint;
  private final boolean customEndpoint;

  ByokProviderPreset(
      String id, BYOKProvider provider, String fixedEndpoint, boolean customEndpoint) {
    this.id = id;
    this.provider = provider;
    this.fixedEndpoint = fixedEndpoint;
    this.customEndpoint = customEndpoint;
  }

  @JsonValue
  @Override
  public String id() {
    return id;
  }

  public BYOKProvider provider() {
    return provider;
  }

  public String fixedEndpoint() {
    return fixedEndpoint;
  }

  public boolean customEndpoint() {
    return customEndpoint;
  }

  @JsonCreator
  public static ByokProviderPreset fromId(String id) {
    return Stream.of(values())
        .filter(preset -> preset.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("Unknown ByokProviderPreset id: " + id));
  }
}
