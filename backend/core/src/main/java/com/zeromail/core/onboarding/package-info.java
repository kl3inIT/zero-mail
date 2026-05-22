@ApplicationModule(
        displayName = "Onboarding",
        allowedDependencies = {
            "tenant",
            "account",
            "account :: domain",
            "account :: usecases",
            "shared :: privacy",
            "shared :: persistence",
            "shared :: lang"
        })
package com.zeromail.core.onboarding;

import org.springframework.modulith.ApplicationModule;
