package com.zeromail.core.billing.model;

import java.util.UUID;

/**
 * UUID-wrapping handle returned by {@link CreditLedger#reserve(UUID, CallSite)} and consumed
 * by {@link CreditLedger#settle(ReservationId)} / {@link CreditLedger#release(ReservationId)}.
 * The wrapper keeps tenant UUIDs and reservation UUIDs distinct at the call site.
 */
public record ReservationId(UUID value) {
}
