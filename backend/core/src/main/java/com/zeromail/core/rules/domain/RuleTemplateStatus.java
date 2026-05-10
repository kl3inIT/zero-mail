package com.zeromail.core.rules.domain;

import java.util.NoSuchElementException;
import java.util.stream.Stream;

import tools.jackson.annotation.JsonCreator;
import tools.jackson.annotation.JsonValue;
import com.zeromail.core.shared.lang.IdentifiedEnum;

public enum RuleTemplateStatus implements IdentifiedEnum {
  MATERIALIZABLE("materializable"),
  GALLERY_ONLY("gallery_only"),
  DEPRECATED("deprecated");

  private final String id;

  RuleTemplateStatus(String id) {
    this.id = id;
  }

  @JsonValue
  @Override
  public String id() {
    return id;
  }

  @JsonCreator
  public static RuleTemplateStatus fromId(String id) {
    return Stream.of(values())
        .filter(ruleTemplateStatus -> ruleTemplateStatus.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("Unknown RuleTemplateStatus id: " + id));
  }
}
