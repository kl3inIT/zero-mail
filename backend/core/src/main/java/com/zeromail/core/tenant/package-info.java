@ApplicationModule(
        displayName = "Tenant",
        allowedDependencies = {"shared :: privacy", "shared :: persistence"})
package com.zeromail.core.tenant;

import org.springframework.modulith.ApplicationModule;
