/**
 * Gmail-domain HTTP wire DTOs ({@code GmailConnectionStatusResponse}).
 *
 * <p>Exposed as part of the {@code dto} application module's public API so
 * {@code com.zeromail.api.controllers.tenant.TenantStatusController} can reach the records
 * across module boundaries (Phase 1.2.1 D-D1 group-by-domain reorg pushed
 * the DTOs into nested packages — by Spring Modulith default those types
 * would be internal; the {@link org.springframework.modulith.NamedInterface}
 * annotation here re-exposes them).
 */
@org.springframework.modulith.NamedInterface("gmail")
package com.zeromail.api.dto.gmail;
