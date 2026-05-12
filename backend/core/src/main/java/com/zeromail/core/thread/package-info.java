/** Thread reply-status classification and metadata-only inbox state. */
@ApplicationModule(
        displayName = "Thread",
        allowedDependencies = {"gmail", "tenant", "shared.persistence", "shared.lang"})
package com.zeromail.core.thread;

import org.springframework.modulith.ApplicationModule;
