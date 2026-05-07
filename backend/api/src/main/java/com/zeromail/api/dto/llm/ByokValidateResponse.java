package com.zeromail.api.dto.llm;

import java.util.List;

public record ByokValidateResponse(boolean ok, List<String> models, String reason) {

  public ByokValidateResponse {
    models = models == null ? null : List.copyOf(models);
  }
}
