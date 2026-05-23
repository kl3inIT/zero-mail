package com.zeromail.core.waitlist.exception;

import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

/**
 * Raised when an admin action targets a waitlist entry whose status does not allow the transition
 * (e.g. approving an already-APPROVED row, rejecting an INVITED row).
 */
public class WaitlistEntryStateException extends BusinessException {

    private final String currentStatus;
    private final String attemptedAction;

    public WaitlistEntryStateException(String currentStatus, String attemptedAction) {
        super("Cannot " + attemptedAction + " waitlist entry in state " + currentStatus);
        this.currentStatus = currentStatus;
        this.attemptedAction = attemptedAction;
    }

    public String currentStatus() {
        return currentStatus;
    }

    public String attemptedAction() {
        return attemptedAction;
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.CONFLICT;
    }

    @Override
    public String errorCode() {
        return "error.waitlist.invalid_state";
    }

    @Override
    public String logEvent() {
        return "waitlist_invalid_state_transition";
    }

    @Override
    public String title() {
        return "Invalid waitlist state transition";
    }

    @Override
    public String detail() {
        return "The waitlist entry is not in a state that allows the requested action.";
    }
}
