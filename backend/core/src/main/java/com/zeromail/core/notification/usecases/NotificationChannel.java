package com.zeromail.core.notification.usecases;

import com.zeromail.core.notification.domain.DigestPayload;

public interface NotificationChannel {

    DispatchOutcome dispatch(DigestPayload payload, String recipientAddress);
}
