@ApplicationModule(
        displayName = "Gmail",
        allowedDependencies = {
            "tenant",
            "config",
            "shared :: crypto",
            "shared :: privacy",
            "shared :: persistence",
            "shared :: lang"
        })
package com.zeromail.core.gmail;

import org.springframework.modulith.ApplicationModule;
