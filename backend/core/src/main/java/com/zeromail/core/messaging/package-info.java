@ApplicationModule(
        displayName = "Messaging",
        allowedDependencies = {
            "gmail :: persistence",
            "tenant",
            "shared :: persistence",
            "shared :: lang"
        })
package com.zeromail.core.messaging;

import org.springframework.modulith.ApplicationModule;
