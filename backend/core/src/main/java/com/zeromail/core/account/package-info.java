@ApplicationModule(
        displayName = "Account",
        allowedDependencies = {
            "tenant",
            "onboarding",
            "gmail",
            "notification",
            "shared.privacy",
            "shared.persistence",
            "shared.lang"
        })
package com.zeromail.core.account;

import org.springframework.modulith.ApplicationModule;
