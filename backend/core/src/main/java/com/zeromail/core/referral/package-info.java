@ApplicationModule(
        displayName = "Referral",
        allowedDependencies = {
            "tenant",
            "shared :: exception",
            "shared :: lang",
            "shared :: persistence"
        })
package com.zeromail.core.referral;

import org.springframework.modulith.ApplicationModule;
