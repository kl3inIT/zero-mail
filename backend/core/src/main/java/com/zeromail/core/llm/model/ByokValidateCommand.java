package com.zeromail.core.llm.model;

import java.util.Objects;

public record ByokValidateCommand(BYOKProvider provider, String endpoint, String apiKey) {

  public ByokValidateCommand {
    Objects.requireNonNull(provider, "provider");
    Objects.requireNonNull(apiKey, "apiKey");
  }
}
