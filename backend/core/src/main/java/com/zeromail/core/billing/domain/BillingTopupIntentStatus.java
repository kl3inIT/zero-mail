package com.zeromail.core.billing.domain;

import java.util.NoSuchElementException;
import java.util.stream.Stream;

import com.zeromail.core.shared.lang.IdentifiedEnum;

/**
 * Lifecycle of a SePay top-up intent. Intents are PENDING on create, PAID on webhook
 * success, and EXPIRED after the payment TTL elapses without a matching payment.
 */
public enum BillingTopupIntentStatus implements IdentifiedEnum {

    PENDING,
    PAID,
    EXPIRED;

    @Override
    public String id() {
        return name();
    }

    public static BillingTopupIntentStatus fromId(String id) {
        return Stream.of(values())
                .filter(intentStatus -> intentStatus.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown BillingTopupIntentStatus id: " + id));
    }
}
