/**
 * Billing domain: prepaid credit ledger with reserve, settle, and release semantics.
 *
 * <p><b>Cross-phase contract.</b> Phase 2C ({@code core.llm.LlmGateway}) imports {@link
 * com.zeromail.core.billing.service.CreditLedger} verbatim and calls {@code reserve(tenantId,
 * callSite)} on the gateway pre-call path when no BYOK credential exists for the tenant.
 *
 * <p><b>Modulith boundary (D-G1).</b> Allowed dependencies are restricted to {@code tenant}
 * (TenantContext), {@code shared.persistence} (AbstractTenantOwnedEntity), and {@code shared.lang}
 * (IdentifiedEnum). There is no edge to the privacy module because billing has no
 * Sensitive&lt;T&gt; payloads, and no edge to {@code account}, {@code gmail}, or {@code
 * onboarding}.
 *
 * <p><b>Sub-packages (D-G2):</b>
 *
 * <ul>
 *   <li>{@code model} - public records, enums, and exceptions.
 *   <li>{@code service} - public service contract and implementation.
 *   <li>{@code persistence} - JPA entities and repositories, added in Plan 03.
 *   <li>{@code persistence.lowlevel} - raw JDBC for {@code pg_advisory_xact_lock},
 *       ArchUnit-allowlisted and added in Plan 03.
 * </ul>
 */
@ApplicationModule(
        displayName = "Billing",
        allowedDependencies = {"tenant", "shared.persistence", "shared.lang"})
package com.zeromail.core.billing;

import org.springframework.modulith.ApplicationModule;
