package com.zeromail.api.error;

public class AuditExportTooLargeException extends RuntimeException {

    public AuditExportTooLargeException() {
        super("Admin audit export exceeds the 10000 row limit");
    }
}
