@ApplicationModule(
        displayName = "Admin",
        allowedDependencies = {
            "tenant",
            "tenant :: usecases",
            "config",
            "billing :: persistence",
            "billing :: usecases",
            "gmail",
            "gmail :: usecases",
            "llm :: domain",
            "llm :: gateway.springai",
            "llm :: routing",
            "rules :: catalog.usecases",
            "shared :: crypto",
            "shared :: error",
            "shared :: exception",
            "shared :: persistence",
            "shared :: lang"
        })
package com.zeromail.core.admin;

import org.springframework.modulith.ApplicationModule;
