package com.zeromail.core.rules.model;

public class RuleValidationException extends RuntimeException {

  private final Reason reason;

  private RuleValidationException(Reason reason) {
    super(reason.message);
    this.reason = reason;
  }

  public static RuleValidationException notFound() {
    return new RuleValidationException(Reason.NOT_FOUND);
  }

  public static RuleValidationException previewRequired() {
    return new RuleValidationException(Reason.PREVIEW_REQUIRED);
  }

  public static RuleValidationException versionMismatch() {
    return new RuleValidationException(Reason.VERSION_MISMATCH);
  }

  public static RuleValidationException invalidReorder() {
    return new RuleValidationException(Reason.INVALID_REORDER);
  }

  public Reason reason() {
    return reason;
  }

  public enum Reason {
    NOT_FOUND("rules.not_found"),
    PREVIEW_REQUIRED("rules.preview_required"),
    VERSION_MISMATCH("rules.version_mismatch"),
    INVALID_REORDER("rules.invalid_reorder");

    private final String message;

    Reason(String message) {
      this.message = message;
    }
  }
}
