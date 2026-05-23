@ApplicationModule(
        displayName = "Notification",
        allowedDependencies = {
            "analytics",
            "analytics :: domain",
            "analytics :: projection",
            "analytics :: usecases",
            "tenant",
            "shared :: persistence",
            "shared :: lang"
        })
package com.zeromail.core.notification;

import org.springframework.modulith.ApplicationModule;
