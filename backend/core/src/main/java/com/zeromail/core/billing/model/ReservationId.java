package com.zeromail.core.billing.model;

import java.util.UUID;

/**
 * UUID-wrapping handle returned by the ledger reserve operation and consumed by settle/release.
 * The wrapper keeps tenant UUIDs and reservation UUIDs distinct at the call site.
 */
public record ReservationId(UUID value) {
}
