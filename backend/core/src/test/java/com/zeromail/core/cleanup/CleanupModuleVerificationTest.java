package com.zeromail.core.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
                                                        + " @ApplicationModule(allowedDependencies = "
                                                        + "{\"gmail\", \"triage\", \"analytics\","
                                                        + " \"tenant\", \"shared :: privacy\","
                                                        + " \"shared :: persistence\","
                                                        + " \"shared :: lang\"})"));

        assertThat(cleanupModule.getName())
                .as("Module name must match 'cleanup'")
                .isEqualTo("cleanup");

        // Full Modulith verify catches any cross-module access that violates the
        // allowedDependencies declared in package-info.java.
        assertThatCode(applicationModules::verify)
                .as(
                        "core.cleanup must not access modules outside its allow-list:"
                                + " gmail, triage, analytics, tenant, shared :: privacy,"
                                + " shared :: persistence, shared :: lang")
                .doesNotThrowAnyException();
    }
}
