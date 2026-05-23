@ApplicationModule(
        displayName = "Admin",
        allowedDependencies = {
            "tenant",
            "config",
            "gmail",
            "gmail :: usecases",
            "shared :: crypto",
            "shared :: error",
            "shared :: exception",
            "shared :: persistence",
            "shared :: lang"
        })
package com.zeromail.core.admin;

import org.springframework.modulith.ApplicationModule;
