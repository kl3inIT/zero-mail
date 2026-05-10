package com.zeromail.core.rules.domain;

import java.util.NoSuchElementException;
import java.util.stream.Stream;

import tools.jackson.annotation.JsonCreator;
import tools.jackson.annotation.JsonValue;
import com.zeromail.core.shared.lang.IdentifiedEnum;

public enum RuleConflictType implements IdentifiedEnum {
  MULTIPLE_DIFFERENT_LABELS("multiple_different_labels"),
  ARCHIVE_AND_SAVE_DRAFT("archive_and_save_draft"),
  DUPLICATE_DRAFT_INTENT("duplicate_draft_intent"),
  CATEGORY_LABEL_MISMATCH("category_label_mismatch");

  private final String id;

  RuleConflictType(String id) {
    this.id = id;
  }

  @JsonValue
  @Override
  public String id() {
    return id;
  }

  @JsonCreator
  public static RuleConflictType fromId(String id) {
    return Stream.of(values())
        .filter(ruleConflictType -> ruleConflictType.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("Unknown RuleConflictType id: " + id));
  }
}
