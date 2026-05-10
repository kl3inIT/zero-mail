package com.zeromail.core.rules.domain;

import java.util.NoSuchElementException;
import java.util.stream.Stream;

import tools.jackson.annotation.JsonCreator;
import tools.jackson.annotation.JsonValue;
import com.zeromail.core.shared.lang.IdentifiedEnum;

public enum RuleLanguage implements IdentifiedEnum {
  EN("en"),
  VI("vi"),
  UNKNOWN("unknown");

  private final String id;

  RuleLanguage(String id) {
    this.id = id;
  }

  @JsonValue
  @Override
  public String id() {
    return id;
  }

  @JsonCreator
  public static RuleLanguage fromId(String id) {
    return Stream.of(values())
        .filter(ruleLanguage -> ruleLanguage.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("Unknown RuleLanguage id: " + id));
  }
}
