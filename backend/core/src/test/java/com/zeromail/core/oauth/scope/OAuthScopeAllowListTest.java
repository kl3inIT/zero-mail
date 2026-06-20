package com.zeromail.core.oauth.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Source-text scanner for the INFRA-01 OAuth scope ledger (D-02).
 *
 * <p>Walks {@code backend/{api,core,worker}/src/main/java} from the repository root and fails CI if
 * any production source file outside {@code
 * backend/core/src/main/java/com/zeromail/core/oauth/scope/} contains a literal Google scope URL of
 * the form {@code https://www.googleapis.com/auth/...}.
 *
 * <p>Implemented as a JUnit 5 file walk rather than an ArchUnit byte-code rule because ArchUnit's
 * {@code JavaClassProcessor} drops constant string argument values during ASM import (see Phase 12
 * RESEARCH.md §Pitfall 1 lines 685-700). A {@code noClasses().that()
 * .containAnyConstantMatching(...)} rule trivially passes because the constants are not visible to
 * the byte-code model. Source-text scan honors D-02's INTENT (no stray scope literals in production
 * source) without misleading callers about ArchUnit's API capabilities.
 *
 * <p>Scope: only {@code .java} files under the three production source roots. YAML / properties /
 * documentation / test resources are NOT scanned — {@code application.yml} legitimately carries the
 * same URL strings because YAML is the wire format Spring's OAuth2 Client starter consumes.
 *
 * <p>Allow-listed package: {@code com.zeromail.core.oauth.scope} — the {@link GoogleOAuthScope}
 * enum body is the canonical home for the URL literals.
 */
class OAuthScopeAllowListTest {

    /** Resolved at test runtime so the source pattern never appears verbatim in this file. */
    private static final Pattern GOOGLE_SCOPE_URL_PATTERN =
            Pattern.compile("https://" + "www\\.googleapis\\.com" + "/auth/" + "[a-zA-Z0-9._-]+");

    private static final List<String> PRODUCTION_SOURCE_ROOTS =
            List.of(
                    "backend/api/src/main/java",
                    "backend/core/src/main/java",
                    "backend/worker/src/main/java");

    private static final String ALLOWED_PACKAGE_PATH =
            "backend/core/src/main/java/com/zeromail/core/oauth/scope";

    @Test
    void production_source_outside_the_ledger_package_carries_no_google_scope_url_literals()
            throws IOException {
        Path repositoryRoot = locateRepositoryRoot();
        List<String> offendingLines = new ArrayList<>();

        for (String sourceRoot : PRODUCTION_SOURCE_ROOTS) {
            Path absoluteRoot = repositoryRoot.resolve(sourceRoot);
            if (!Files.exists(absoluteRoot)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(absoluteRoot)) {
                walk.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> !isInsideAllowedPackage(repositoryRoot, path))
                        .forEach(
                                path -> {
                                    try {
                                        scanFile(repositoryRoot, path, offendingLines);
                                    } catch (IOException readFailure) {
                                        throw new IllegalStateException(
                                                "Failed to read " + path, readFailure);
                                    }
                                });
            }
        }

        if (!offendingLines.isEmpty()) {
            fail(
                    "INFRA-01 source-text scanner found "
                            + offendingLines.size()
                            + " stray Google OAuth scope URL literal(s) outside "
                            + ALLOWED_PACKAGE_PATH
                            + ". Move them through GoogleOAuthScope.X.value() instead.\n  - "
                            + String.join("\n  - ", offendingLines));
        }
    }

    @Test
    void scanner_catches_a_seeded_violation_in_a_temporary_production_like_file(
            @TempDir Path tempDir) throws IOException {
        // Validates the scanner DOES catch violations by writing a synthetic .java file with the
        // forbidden URL pattern and running the same regex over it. Avoids planting a literal in
        // any real production source.
        String forbiddenUrl = "https://" + "www.googleapis.com/auth/" + "calendar";
        Path syntheticProductionFile = tempDir.resolve("SyntheticProduction.java");
        Files.writeString(
                syntheticProductionFile,
                "package x;\npublic final class SyntheticProduction {\n  String s = \""
                        + forbiddenUrl
                        + "\";\n}\n");

        List<String> diagnostics = new ArrayList<>();
        scanFile(tempDir, syntheticProductionFile, diagnostics);

        assertThat(diagnostics)
                .as("scanner must report exactly one offending line for the seeded file")
                .hasSize(1);
        assertThat(diagnostics.get(0)).contains("SyntheticProduction.java").contains(forbiddenUrl);
    }

    private static boolean isInsideAllowedPackage(Path repositoryRoot, Path candidate) {
        Path allowedAbsolute = repositoryRoot.resolve(ALLOWED_PACKAGE_PATH).toAbsolutePath();
        return candidate.toAbsolutePath().startsWith(allowedAbsolute);
    }

    private static void scanFile(Path baseForRelative, Path sourceFile, List<String> sink)
            throws IOException {
        List<String> lines = Files.readAllLines(sourceFile);
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            String line = lines.get(lineIndex);
            Matcher matcher = GOOGLE_SCOPE_URL_PATTERN.matcher(line);
            while (matcher.find()) {
                String relativePath =
                        baseForRelative
                                .toAbsolutePath()
                                .relativize(sourceFile.toAbsolutePath())
                                .toString()
                                .replace('\\', '/');
                sink.add(relativePath + ":" + (lineIndex + 1) + " " + matcher.group());
            }
        }
    }

    /**
     * Walks up from {@code user.dir} until a directory containing {@code settings.gradle.kts} is
     * found. Lets the test run from any working directory (root, {@code backend/}, or a
     * sub-project).
     */
    private static Path locateRepositoryRoot() {
        Path candidate = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (candidate != null) {
            if (Files.exists(candidate.resolve("settings.gradle.kts"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException(
                "Could not locate repository root (no settings.gradle.kts found walking up from "
                        + System.getProperty("user.dir")
                        + ")");
    }
}
