package com.zeromail.core.llm.model;

import java.util.Objects;

public record ByokSaveCommand(BYOKProvider provider, String endpoint, String apiKey) {

  public ByokSaveCommand {
    Objects.requireNonNull(provider, "provider");
    Objects.requireNonNull(apiKey, "apiKey");
  }
}
