@ApplicationModule(
        displayName = "Account",
        allowedDependencies = {
            "mailbox",
            "tenant",
            "tenant :: usecases",
            "gmail",
            "gmail :: usecases",
            "gmail :: persistence.crypto",
            "inbox :: usecases",
            "notification",
            "notification :: domain",
            "notification :: usecases",
            "shared :: privacy",
            "shared :: error",
            "shared :: exception",
            "shared :: persistence",
            "shared :: lang"
        })
package com.zeromail.core.account;

import org.springframework.modulith.ApplicationModule;
