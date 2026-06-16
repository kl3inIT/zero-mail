/**
 * Outbound mail execution boundary.
 *
 * <p>Phase 08.1 introduces this module as the shared owner for actual Gmail sends. Chat
 * confirmations and rule-triggered outbound automation must call the named API surface instead of
 * calling Gmail send APIs directly.
 */
@ApplicationModule(
        displayName = "Outbound",
        allowedDependencies = {
            "mailbox",
            "gmail",
            "gmail :: gateway",
            "tenant",
            "tenant :: usecases",
            "shared :: exception",
            "shared :: lang"
        })
package com.zeromail.core.outbound;

import org.springframework.modulith.ApplicationModule;
