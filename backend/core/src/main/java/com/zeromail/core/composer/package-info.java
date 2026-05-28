/**
 * Composer module: user-initiated reply/forward draft caching.
 *
 * <p>Persists the inbox reply composer body and headers as a real Gmail draft via {@code
 * users.drafts.create/update}, so navigating away from the inbox and coming back (or opening Gmail
 * on another device) restores the in-progress draft. All Gmail writes are routed through {@code
 * core.triage.usecases.TriageGmailWriter} per the {@code GmailWriteBoundaryTest} arch contract.
 *
 * <p>The composer draft body is user-authored content (the draft-body carve-out in CLAUDE.md
 * privacy section explicitly allows persisting it). It is intentionally NOT mirrored into any
 * application database — Gmail itself is the source of truth.
 */
@ApplicationModule(
        displayName = "Composer",
        allowedDependencies = {
            "triage",
            "triage :: domain",
            "triage :: usecases",
            "tenant",
            "tenant :: usecases",
            "shared :: error",
            "shared :: exception",
            "shared :: lang"
        })
package com.zeromail.core.composer;

import org.springframework.modulith.ApplicationModule;
