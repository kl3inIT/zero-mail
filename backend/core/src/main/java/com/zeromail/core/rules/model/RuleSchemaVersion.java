package com.zeromail.core.rules.model;

import java.util.NoSuchElementException;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.zeromail.core.shared.lang.IdentifiedEnum;

public enum RuleSchemaVersion implements IdentifiedEnum {
  RULES_V1("rules.v1");

  public static final String RULES_V1_ID = "rules.v1";

  private final String id;

  RuleSchemaVersion(String id) {
    this.id = id;
  }

  @JsonValue
  @Override
  public String id() {
    return id;
  }

  @JsonCreator
  public static RuleSchemaVersion fromId(String id) {
    return Stream.of(values())
        .filter(ruleSchemaVersion -> ruleSchemaVersion.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("Unknown RuleSchemaVersion id: " + id));
  }
}
