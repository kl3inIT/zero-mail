package com.zeromail.core.rules.exception;

import java.util.Objects;

public class GmailPreviewUnavailableException extends RuntimeException {

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
