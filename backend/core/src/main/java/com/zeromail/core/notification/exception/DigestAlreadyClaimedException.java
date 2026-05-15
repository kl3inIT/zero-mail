package com.zeromail.core.notification.exception;

import java.time.LocalDate;
import java.util.UUID;

public class DigestAlreadyClaimedException extends RuntimeException {

    public DigestAlreadyClaimedException(UUID tenantId, LocalDate digestDayLocal, Throwable cause) {
        super(
                "Digest already claimed for tenantId=%s digestDayLocal=%s"
                        .formatted(tenantId, digestDayLocal),
                cause);
    }
}
