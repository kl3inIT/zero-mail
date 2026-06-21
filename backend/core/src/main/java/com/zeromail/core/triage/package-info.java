/**
 * Triage domain: event-driven mail triage orchestration, safety policy, Gmail action execution,
 * audit, undo, and sender safety net services.
 */
@ApplicationModule(
        displayName = "Triage",
        allowedDependencies = {
            "mailbox",
            "rules",
            "rules :: domain",
            "rules :: projection",
            "rules :: usecases",
            "gmail",
            "gmail :: domain",
            "gmail :: events",
            "gmail :: gateway",
            "gmail :: usecases",
            "llm",
            "llm :: usecases",
            "draft",
            "thread",
            "thread :: usecases",
            "inbox :: domain",
            "inbox :: usecases",
            "outbound",
            "outbound :: api",
            "billing",
            "billing :: domain",
            "billing :: usecases",
            "tenant",
            "tenant :: usecases",
            "shared :: crypto",
            "shared :: error",
            "shared :: exception",
            "shared :: privacy",
            "shared :: persistence",
            "shared :: lang",
            "shared :: pagination",
            "shared :: validation"
        })
package com.zeromail.core.triage;

import org.springframework.modulith.ApplicationModule;
