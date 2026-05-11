/**
 * Triage domain: event-driven mail triage orchestration, safety policy, Gmail action
 * execution, audit, undo, and sender safety net services.
 */
@ApplicationModule(
    displayName = "Triage",
    allowedDependencies = {"rules", "gmail", "llm", "billing", "tenant", "shared.persistence", "shared.lang"})
package com.zeromail.core.triage;

import org.springframework.modulith.ApplicationModule;
