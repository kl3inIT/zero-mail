package com.zeromail.worker.llm;

import java.util.Map;
import java.util.Objects;

public record DriftFixture(
    String id,
    String subject,
    String from,
    String htmlBody,
    String expectedAction,
    Map<String, Object> expectedArgs) {

  public DriftFixture {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(htmlBody, "htmlBody");
    Objects.requireNonNull(expectedAction, "expectedAction");
    expectedArgs = expectedArgs == null ? Map.of() : Map.copyOf(expectedArgs);
  }

  public String prompt() {
    return "Subject: " + subject + "\nFrom: " + from + "\n\n" + htmlBody;
  }
}
