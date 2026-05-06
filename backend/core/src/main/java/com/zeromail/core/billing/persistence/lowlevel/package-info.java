/**
 * Allow-listed package for native SQL / raw JDBC inside the billing domain.
 *
 * <p>Phase 2B introduces {@code AdvisoryLockJdbcHelper} here for
 * {@code SELECT pg_advisory_xact_lock(hashtext(?))}. The transaction-scoped advisory
 * lock is released on commit or rollback and wraps the SUM-balance check plus RESERVE
 * insert critical section per CONTEXT D-A1.
 *
 * <p><b>ArchUnit guard (Plan 06):</b> No class outside this sub-package may use
 * {@code org.springframework.jdbc.core.JdbcTemplate}. This mirrors
 * {@code core.gmail.persistence.lowlevel}: an intra-domain marker, not a separate
 * Modulith module.
 */
package com.zeromail.core.billing.persistence.lowlevel;
