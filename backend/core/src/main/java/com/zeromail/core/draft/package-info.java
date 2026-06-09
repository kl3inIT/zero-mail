/** Draft-reply generation, tone context, and on-demand draft orchestration. */
@ApplicationModule(
        displayName = "Draft",
        allowedDependencies = {
            "llm",
            "llm :: domain",
            "llm :: exception",
            "llm :: gateway.sanitization",
            "llm :: usecases",
            "chat :: settings-api",
            "billing",
            "billing :: domain",
            "triage",
            "triage :: domain",
            "triage :: usecases",
            "rules",
            "rules :: domain",
            "gmail",
            "gmail :: gateway",
            "gmail :: usecases",
            "thread",
            "thread :: events",
            "thread :: usecases",
            "tenant",
            "shared :: error",
            "shared :: exception",
            "shared :: persistence",
            "shared :: lang",
            "shared :: lock"
        })
package com.zeromail.core.draft;

import org.springframework.modulith.ApplicationModule;
