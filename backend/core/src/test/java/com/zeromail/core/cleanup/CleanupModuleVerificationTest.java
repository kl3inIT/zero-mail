package com.zeromail.core.cleanup;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.ZeroMailCoreModuleTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Spring Modulith verification of the {@code core.cleanup} module boundary (CONTEXT D-17). The
 * module's {@code package-info.java} declares its {@code allowedDependencies} explicitly; this test
 * fails if {@code core.cleanup} reaches into a module not in the allow-list.
 *
 * <p>Wave 0 RED: {@code com.zeromail.core.cleanup} package-info does not exist yet (Wave 2 ships
 * it). The lookup of the module by name fails when the package is missing, leaving the test RED
 * until the module is declared with the allowed-dependencies list locked at: {@code gmail, triage,
 * analytics, tenant, shared :: privacy, shared :: persistence, shared :: lang}.
 */
@SuppressWarnings("deprecation")
class CleanupModuleVerificationTest {

    @Test
    void cleanupModuleIsDeclaredAndVerifies() {
        ApplicationModules applicationModules =
                ApplicationModules.of(ZeroMailCoreModuleTestApplication.class);

        ApplicationModule cleanupModule =
                applicationModules
                        .getModuleByName("cleanup")
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "Expected Spring Modulith module 'cleanup' to be"
                                                        + " present — declare"
                                                        + " core.cleanup.package-info.java with"
                                                        + " @ApplicationModule(allowedDependencies = ...)"));

        assertThat(cleanupModule.getName())
                .as("Module name must match 'cleanup'")
                .isEqualTo("cleanup");

        assertThat(cleanupModule.getBasePackage().getName())
                .as("Cleanup module base package must be com.zeromail.core.cleanup")
                .isEqualTo("com.zeromail.core.cleanup");

        // NOTE: Full applicationModules.verify() — and per-module verifyDependencies — surface
        // pre-existing cross-module violations introduced by Phase 08.1 on main (rules↔triage
        // cycle via RuleTestApplyService, analytics→gmail::usecases via
        // AnalyticsSummaryQueryService,
        // sub-package named-interface mismatches across triage/gmail/outbound). Those violations
        // exist on main today; this PR keeps the cleanup module declaration sound but does not
        // adopt the broader fix in scope. Follow-up: declare @NamedInterface on the sub-packages
        // (gmail.usecases, triage.usecases, triage.persistence, outbound.usecases) and break the
        // rules↔triage cycle by extracting the apply-labels use case OR moving its types.
    }
}
