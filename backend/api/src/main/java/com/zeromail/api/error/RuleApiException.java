package com.zeromail.api.error;

public class RuleApiException extends RuntimeException {

  private final Reason reason;

  private RuleApiException(Reason reason) {
    super(reason.name());
    this.reason = reason;
  }

  public static RuleApiException invalidCompileOutput() {
    return new RuleApiException(Reason.INVALID_COMPILE_OUTPUT);
  }

  public static RuleApiException clarificationRequired() {
    return new RuleApiException(Reason.CLARIFICATION_REQUIRED);
  }

  public static RuleApiException invalidSampleSize() {
    return new RuleApiException(Reason.INVALID_SAMPLE_SIZE);
  }

  public static RuleApiException invalidReorder() {
    return new RuleApiException(Reason.INVALID_REORDER);
  }

  public static RuleApiException unsafeAction() {
    return new RuleApiException(Reason.UNSAFE_ACTION);
  }

  public Reason reason() {
    return reason;
  }

  public enum Reason {
    INVALID_COMPILE_OUTPUT,
    CLARIFICATION_REQUIRED,
    INVALID_SAMPLE_SIZE,
    INVALID_REORDER,
    UNSAFE_ACTION
  }
}
