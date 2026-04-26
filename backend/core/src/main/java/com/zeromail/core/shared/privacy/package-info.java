/**
 * Cross-cutting Sensitive&lt;T&gt; marker contract: redaction wrapper, Jackson module, Logback scrub filter.
 * The only inhabitant of {@code core.shared/} in Phase 1.2.
 *
 * <p>Spring Modulith naming form for cross-sibling references: TBD by Plan 01.2-01 Task 3 probe.
 * (CL-3 — the working form will be inserted here and applied identically across all package-info files.)
 */
@ApplicationModule(displayName = "Privacy", allowedDependencies = {})
package com.zeromail.core.shared.privacy;

import org.springframework.modulith.ApplicationModule;
