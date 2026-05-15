package com.zeromail.api.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(packages = "com.zeromail", importOptions = ImportOption.DoNotIncludeTests.class)
class LaunchProfileArchUnitTest {

    private static final Pattern TEST_PROFILE_ENABLED =
            Pattern.compile("zeromail\\.(loadtest|e2e-stub)\\.enabled\\s*:\\s*true");
    private static final List<String> EXCLUDED_PROFILE_YMLS =
            List.of("application-e2e-stub.yml", "application-loadtest.yml");

    @ArchTest
    static final ArchRule production_does_not_reference_e2estub =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("..api.e2estub..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..api.e2estub..")
                    .because(
                            "D-07: e2e-stub beans are test infrastructure; production code must not reach them.");

    @ArchTest
    static final ArchRule production_does_not_reference_loadtest =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("..api.loadtest..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..api.loadtest..")
                    .because(
                            "D-03: loadtest beans are test infrastructure; production code must not reach them.");

    @Test
    void production_ymls_do_not_activate_test_profiles() throws IOException {
        Path projectRoot = projectRoot();
        List<Path> scanned = scanApplicationYmls();
        List<String> violations = new ArrayList<>();

        assertThat(scanned)
                .as("Rule C must scan api + worker production application yml files")
                .contains(
                        projectRoot.resolve("backend/api/src/main/resources/application.yml"),
                        projectRoot.resolve("backend/worker/src/main/resources/application.yml"));

        for (Path file : scanned) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.contains("e2e-stub")
                    || content.contains("loadtest")
                    || TEST_PROFILE_ENABLED.matcher(content).find()) {
                violations.add(file.toString());
            }
        }

        assertThat(violations)
                .as(
                        "Production application*.yml files must not activate e2e-stub/loadtest: %s",
                        scanned)
                .isEmpty();
    }

    private static List<Path> scanApplicationYmls() throws IOException {
        Path projectRoot = projectRoot();
        List<Path> resourceDirectories =
                List.of(
                        projectRoot.resolve("backend/api/src/main/resources"),
                        projectRoot.resolve("backend/worker/src/main/resources"));
        List<Path> scanned = new ArrayList<>();
        for (Path resourceDirectory : resourceDirectories) {
            if (!Files.isDirectory(resourceDirectory)) {
                continue;
            }
            try (Stream<Path> files = Files.list(resourceDirectory)) {
                files.filter(LaunchProfileArchUnitTest::isProductionApplicationYml)
                        .sorted()
                        .forEach(scanned::add);
            }
        }
        return scanned;
    }

    private static boolean isProductionApplicationYml(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.startsWith("application")
                && fileName.endsWith(".yml")
                && !EXCLUDED_PROFILE_YMLS.contains(fileName);
    }

    private static Path projectRoot() {
        Path currentDirectory = Path.of("").toAbsolutePath();
        while (currentDirectory != null) {
            if (Files.exists(currentDirectory.resolve("settings.gradle.kts"))) {
                return currentDirectory;
            }
            currentDirectory = currentDirectory.getParent();
        }
        throw new IllegalStateException("Could not locate project root from current directory");
    }
}
