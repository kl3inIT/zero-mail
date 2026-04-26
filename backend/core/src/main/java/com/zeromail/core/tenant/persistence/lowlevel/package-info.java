/**
 * Allow-listed package for native SQL / raw JDBC inside the tenant domain.
 * ArchUnit rule {@code ..persistence.lowlevel..} excludes this package from the
 * "no native SQL" rule. Every class in here MUST manually apply tenant_id = ?.
 * Empty in Phase 1.2 — first inhabitant lands when a future phase needs SKIP LOCKED queue polling.
 */
package com.zeromail.core.tenant.persistence.lowlevel;
