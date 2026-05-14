package com.zeromail.core.notification.usecases;

import com.zeromail.core.notification.domain.ChannelType;
import java.time.LocalDate;
import java.util.UUID;

public record DigestClaimRecord(
        UUID deliveryId,
        UUID tenantId,
        LocalDate digestDayLocal,
        int attemptCount,
        ChannelType channel) {}
