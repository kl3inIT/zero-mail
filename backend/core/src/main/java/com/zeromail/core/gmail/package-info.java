@ApplicationModule(
        displayName = "Gmail",
        allowedDependencies = {
            "tenant",
            "config",
            "inbox :: domain",
            "inbox :: persistence",
            "inbox :: usecases",
            "mailbox",
            "shared :: crypto",
            "shared :: exception",
            "shared :: html",
            "shared :: privacy",
            "shared :: persistence",
            "shared :: lang"
        })
package com.zeromail.core.gmail;

import org.springframework.modulith.ApplicationModule;
