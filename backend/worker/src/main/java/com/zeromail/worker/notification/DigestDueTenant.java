package com.zeromail.worker.notification;

import java.time.LocalDate;
import java.util.UUID;

public record DigestDueTenant(
        UUID tenantId,
        String timeZone,
        int sendHourLocal,
        String preferredLanguage,
        LocalDate digestDayLocal) {}
