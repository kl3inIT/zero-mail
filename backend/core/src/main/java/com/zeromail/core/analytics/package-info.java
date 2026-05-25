@ApplicationModule(
        displayName = "Analytics",
        allowedDependencies = {
            "triage",
            "gmail",
            "gmail :: usecases",
            "shared :: persistence",
            "shared :: lang"
        })
package com.zeromail.core.analytics;

import org.springframework.modulith.ApplicationModule;
