package com.zeromail.core.billing.domain;

/**
 * Read projection of a tenant's current credit ledger state.
 *
 * <p>{@code availableCredits} is the signed journal sum for the tenant. {@code heldCredits}
 * is the sum of reserved credits that have not yet been finalized by settle or release.
 *
 * <p>Currency is implicit: integer credits. HTTP responses wrap this in a billing balance
 * DTO with {@code currency = "credits"}.
 */
public record CreditBalance(int availableCredits, int heldCredits) {
}
