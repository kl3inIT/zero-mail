@ApplicationModule(
        displayName = "Waitlist",
        allowedDependencies = {
            "account",
            "account :: usecases",
            "shared :: crypto",
            "shared :: persistence",
            "shared :: lang",
            "shared :: exception"
        })
package com.zeromail.core.waitlist;

import org.springframework.modulith.ApplicationModule;
