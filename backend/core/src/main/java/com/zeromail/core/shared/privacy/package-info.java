/**
 * Cross-cutting Sensitive&lt;T&gt; marker contract: redaction wrapper, Jackson module, Logback
 * scrub filter. The only inhabitant of {@code core.shared/} in Phase 1.2.
 *
 * <p><b>Spring Modulith naming form (CL-3 probe locked 2026-04-26):</b> Cross-sibling modules MUST
 * reference this module as {@code "shared.privacy"} in their {@code allowedDependencies} array.
 *
 * <p>Probe results (Plan 01.2-01 Task 3):
 *
 * <ul>
 *   <li>A — {@code "shared.privacy"} (dotted nested form): <b>PASS</b>
 *   <li>B — {@code "privacy"} (local simple-name form): not probed (A passed)
 *   <li>C — {@code "shared :: privacy"} (named-interface form): not probed (A passed)
 * </ul>
 *
 * <p>Spring Modulith 2.x resolves {@code "shared.privacy"} against the package {@code
 * com.zeromail.core.shared.privacy} via base-package suffix matching when the sibling module's base
 * package shares the {@code com.zeromail.core} prefix. Plans 02-05 MUST use this exact literal in
 * their {@code allowedDependencies} arrays.
 */
@ApplicationModule(
        displayName = "Privacy",
        allowedDependencies = {})
package com.zeromail.core.shared.privacy;

import org.springframework.modulith.ApplicationModule;
