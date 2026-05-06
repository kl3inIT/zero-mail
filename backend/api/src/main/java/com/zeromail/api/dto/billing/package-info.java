/**
 * Billing-domain HTTP wire DTOs for balance, top-up intents, and SePay webhooks.
 *
 * <p>Exposed as part of the {@code dto} application module's public API so billing controllers can
 * reach these records across Spring Modulith module boundaries.
 */
@org.springframework.modulith.NamedInterface("billing")
package com.zeromail.api.dto.billing;
