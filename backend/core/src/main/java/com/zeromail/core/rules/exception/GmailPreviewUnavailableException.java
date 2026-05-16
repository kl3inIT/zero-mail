package com.zeromail.core.rules.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.util.Map;
import java.util.Objects;

public class GmailPreviewUnavailableException extends BusinessException {

    public static final String ERROR_KEY = "error.rules.gmail.unavailable";

    private final Reason reason;

    public GmailPreviewUnavailableException(Reason reason) {
        super("Gmail preview unavailable: " + reason.id());
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public Reason reason() {
        return reason;
    }

    public String errorKey() {
        return ERROR_KEY;
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.SERVICE_UNAVAILABLE;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.RULES_GMAIL_UNAVAILABLE;
    }

    @Override
    public String logEvent() {
        return "rules_gmail_preview_unavailable";
    }

    @Override
    public String title() {
        return "Gmail preview unavailable";
    }

    @Override
    public String detail() {
        return "Gmail preview data is not currently available.";
    }

    @Override
    public Map<String, Object> params() {
        return Map.of("reason", reason.id());
    }

    public enum Reason {
        NOT_CONNECTED("not_connected"),
        DISCONNECTED("disconnected"),
        NO_READ_GRANT("no_read_grant"),
        REVOKED("revoked"),
        FETCH_TIMEOUT("fetch_timeout"),
        GMAIL_UNAVAILABLE("gmail_unavailable");

        private final String id;

        Reason(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }
}
