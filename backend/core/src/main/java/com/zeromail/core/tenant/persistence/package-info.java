/**
 * Tenant domain persistence: JPA entity and Spring Data repository for the {@code tenants} table.
 * Intra-domain package — repository is {@code public} (Spring Data JPA proxy requirement, D-D1)
 * but cross-domain repository imports are forbidden by {@code DomainBoundaryArchTests} (Plan 01.2-06).
 */
package com.zeromail.core.tenant.persistence;
