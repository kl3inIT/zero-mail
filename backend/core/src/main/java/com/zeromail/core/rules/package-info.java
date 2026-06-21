/**
 * Rules domain: tenant-owned natural-language rule definitions, typed matcher/action model,
 * persistence, and future management/evaluation services.
 *
 * <p>Allowed dependencies keep model and persistence on existing domain boundaries. Cross-domain
 * reads must go through owning services, not repositories.
 */
@ApplicationModule(
        displayName = "Rules",
        allowedDependencies = {
            "tenant",
            "llm",
            "llm :: domain",
            "llm :: usecases",
            "billing",
            "billing :: domain",
            "gmail",
            "gmail :: domain",
            "gmail :: projection",
            "gmail :: usecases",
            "inbox :: domain",
            "mailbox",
            "onboarding",
            "onboarding :: usecases",
            "shared :: error",
            "shared :: exception",
            "shared :: persistence",
            "shared :: lang",
            "shared :: validation"
        })
package com.zeromail.core.rules;

import org.springframework.modulith.ApplicationModule;
