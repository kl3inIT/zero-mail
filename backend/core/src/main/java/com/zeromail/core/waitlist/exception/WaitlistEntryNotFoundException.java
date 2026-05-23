package com.zeromail.core.waitlist.exception;

import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.util.UUID;

public class WaitlistEntryNotFoundException extends BusinessException {

    private final UUID waitlistId;

    public WaitlistEntryNotFoundException(UUID waitlistId) {
        super("Waitlist entry " + waitlistId + " not found");
        this.waitlistId = waitlistId;
    }

    public UUID waitlistId() {
        return waitlistId;
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.NOT_FOUND;
    }

    @Override
    public String errorCode() {
        return "error.waitlist.entry_not_found";
    }

    @Override
    public String logEvent() {
        return "waitlist_entry_not_found";
    }

    @Override
    public String title() {
        return "Waitlist entry not found";
    }

    @Override
    public String detail() {
        return "The requested waitlist entry does not exist.";
    }
}
