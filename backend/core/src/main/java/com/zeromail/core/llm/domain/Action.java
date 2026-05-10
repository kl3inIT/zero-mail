package com.zeromail.core.llm.domain;

import java.util.NoSuchElementException;
import java.util.stream.Stream;

import com.zeromail.core.shared.lang.IdentifiedEnum;

public enum Action implements IdentifiedEnum {
  LABEL("label"),
  ARCHIVE("archive"),
  SAVE_DRAFT("save_draft");

  private final String functionName;

  Action(String functionName) {
    this.functionName = functionName;
  }

  @Override
  public String id() {
    return functionName;
  }

  public String functionName() {
    return functionName;
  }

  public static Action fromId(String id) {
    return Stream.of(values())
        .filter(action -> action.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("Unknown Action id: " + id));
  }

  public static Action fromFunctionName(String functionName) {
    return Stream.of(values())
        .filter(action -> action.functionName().equals(functionName))
        .findFirst()
        .orElseThrow(
            () -> new NoSuchElementException("Unknown Action functionName: " + functionName));
  }
}
