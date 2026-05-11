package com.zeromail.core.triage.exception;

public class TriageAuditException extends RuntimeException {

  private final Reason reason;

  private TriageAuditException(Reason reason) {
    super(reason.message);
    this.reason = reason;
  }

  public static TriageAuditException unsupportedActionType() {
    return new TriageAuditException(Reason.UNSUPPORTED_ACTION_TYPE);
  }

  public Reason reason() {
    return reason;
  }

  public enum Reason {
    UNSUPPORTED_ACTION_TYPE("triage.unsupported_action_type");

    private final String message;

    Reason(String message) {
      this.message = message;
    }
  }
}
