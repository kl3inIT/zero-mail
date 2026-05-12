/** Draft-reply generation, tone context, and on-demand draft orchestration. */
@ApplicationModule(
        displayName = "Draft",
        allowedDependencies = {
            "llm",
            "triage",
            "gmail",
            "thread",
            "tenant",
            "shared.persistence",
            "shared.lang",
            "shared.lock"
        })
package com.zeromail.core.draft;

import org.springframework.modulith.ApplicationModule;
