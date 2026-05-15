package com.zeromail.core.triage.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

/**
 * Unified signal for every triage-undo failure mode. The previous three-class split (expired /
 * already-done / write-failed) collapsed into a single exception carrying a {@link Reason}; the
 * HTTP layer maps the reason to a dotted error code without case-splitting on exception type.
 */
public class TriageUndoException extends BusinessException {

    private final Reason reason;

    private TriageUndoException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    public static TriageUndoException expired() {
        return new TriageUndoException(Reason.EXPIRED);
    }

    public static TriageUndoException alreadyDone() {
        return new TriageUndoException(Reason.ALREADY_DONE);
    }

    public static TriageUndoException writeFailed() {
        return new TriageUndoException(Reason.WRITE_FAILED);
    }

    public Reason reason() {
        return reason;
    }

    @Override
    public ErrorClass errorClass() {
        return reason.errorClass;
    }

    @Override
    public String errorCode() {
        return reason.errorCode;
    }

    @Override
    public String logEvent() {
        return reason.logEvent;
    }

    @Override
    public String title() {
        return reason.title;
    }

    @Override
    public String detail() {
        return reason.detail;
    }

    public enum Reason {
        EXPIRED(
                ErrorClass.CONFLICT,
                ErrorCodes.TRIAGE_UNDO_EXPIRED,
                "triage.undo.expired",
                "triage_undo_rejected",
                "Triage undo expired",
                "The triage action can no longer be undone."),
        ALREADY_DONE(
                ErrorClass.CONFLICT,
                ErrorCodes.TRIAGE_UNDO_ALREADY_DONE,
                "triage.undo.already_done",
                "triage_undo_rejected",
                "Triage undo unavailable",
                "The triage action is not in an undoable state."),
        WRITE_FAILED(
                ErrorClass.GATEWAY_FAILURE,
                ErrorCodes.TRIAGE_UNDO_WRITE_FAILED,
                "triage.undo.write_failed",
                "triage_undo_write_failed",
                "Triage undo write failed",
                "The triage action could not be undone right now.");

        private final ErrorClass errorClass;
        private final String errorCode;
        private final String message;
        private final String logEvent;
        private final String title;
        private final String detail;

        Reason(
                ErrorClass errorClass,
                String errorCode,
                String message,
                String logEvent,
                String title,
                String detail) {
            this.errorClass = errorClass;
            this.errorCode = errorCode;
            this.message = message;
            this.logEvent = logEvent;
            this.title = title;
            this.detail = detail;
        }
    }
}
