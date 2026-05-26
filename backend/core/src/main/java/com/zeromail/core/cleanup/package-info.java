/**
 * Cleanup domain: bulk unsubscribe campaigns + sender suppression list + generic Postgres-backed
 * processing job outbox (Phase 8).
 *
 * <p><b>Module boundary (D-17):</b> allowedDependencies covers the modules the cleanup pipeline
 * legitimately reaches into — {@code gmail} (Gmail API client for message archive / one-click
 * unsubscribe POST), {@code triage} (TriageAuditWriter.recordCleanupArchive for per-message audit
 * rows; TriageGmailWriter for label/archive primitives), {@code analytics} (read-side projection
 * candidate query feeder), {@code tenant} (TenantContext propagation), and the shared utility
 * modules.
 */
@ApplicationModule(
        displayName = "Cleanup",
        allowedDependencies = {
            "gmail",
            "gmail :: usecases",
            "triage",
            "triage :: usecases",
            "analytics",
            "tenant",
            "outbound",
            "outbound :: api",
            "queue",
            "queue :: domain",
            "shared :: privacy",
            "shared :: persistence",
            "shared :: lang",
            "shared :: exception"
        })
package com.zeromail.core.cleanup;

import org.springframework.modulith.ApplicationModule;
