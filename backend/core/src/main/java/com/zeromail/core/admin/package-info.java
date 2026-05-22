@ApplicationModule(
        displayName = "Admin",
        allowedDependencies = {"tenant", "gmail", "shared.persistence", "shared.lang"})
package com.zeromail.core.admin;

import org.springframework.modulith.ApplicationModule;
