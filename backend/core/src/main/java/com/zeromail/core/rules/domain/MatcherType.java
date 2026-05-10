package com.zeromail.core.rules.domain;

import java.util.NoSuchElementException;
import java.util.stream.Stream;

import tools.jackson.annotation.JsonCreator;
import tools.jackson.annotation.JsonValue;
import com.zeromail.core.shared.lang.IdentifiedEnum;

public enum MatcherType implements IdentifiedEnum {
  SENDER_EMAIL,
  SENDER_DOMAIN,
  RECIPIENT_TO,
  RECIPIENT_CC,
  SUBJECT_CONTAINS,
  SUBJECT_EQUALS,
  SUBJECT_REGEX,
  GMAIL_LABEL_PRESENT,
  GMAIL_LABEL_ABSENT,
  GMAIL_CATEGORY_PRESENT,
  GMAIL_CATEGORY_ABSENT,
  HAS_ATTACHMENT,
  LIST_UNSUBSCRIBE_PRESENT,
  NEWSLETTER_INDICATOR,
  MESSAGE_AGE,
  MESSAGE_DATE,
  ALL,
  ANY,
  NOT,
  SEMANTIC_INTENT;

  @JsonValue
  @Override
  public String id() {
    return name();
  }

  @JsonCreator
  public static MatcherType fromId(String id) {
    return Stream.of(values())
        .filter(matcherType -> matcherType.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("Unknown MatcherType id: " + id));
  }
}
