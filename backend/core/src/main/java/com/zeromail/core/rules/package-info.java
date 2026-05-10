/**
 * Rules domain: tenant-owned natural-language rule definitions, typed matcher/action model,
 * persistence, and future management/evaluation services.
 *
 * <p>Allowed dependencies keep model and persistence on existing domain boundaries.
 * Cross-domain reads must go through owning services, not repositories.
 */
@ApplicationModule(
    displayName = "Rules",
    allowedDependencies = {
      "tenant",
      "llm",
      "gmail",
      "onboarding",
      "shared.persistence",
      "shared.lang"
    })
package com.zeromail.core.rules;

import org.springframework.modulith.ApplicationModule;
