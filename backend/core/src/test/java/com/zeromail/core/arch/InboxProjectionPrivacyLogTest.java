package com.zeromail.core.arch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Static source-file lint: no {@code log.*(...)} call in {@code core.inbox.**} may reference a
 * plaintext field name. The projection encrypts subject / snippet / sender email / sender display
 * name; logging the unencrypted symbol would defeat the privacy invariant the cipher exists to
 * enforce.
 *
 * <p>Detection is structural — for each Java source file under {@code backend/core/.../core/inbox},
 * find every log call ({@code log.info|warn|error|debug|trace}) and assert the captured argument
 * list contains none of the forbidden identifiers as a bare word. Identifiers inside string
 * literals are fine (e.g. {@code "event=inbox_backfill_subject_failed"}) — the regex matches the
 * identifier as a JVM-style symbol, which by design does not show up inside double-quoted strings.
 */
class InboxProjectionPrivacyLogTest {

    private static final Path INBOX_MODULE_ROOT =
            Path.of("src", "main", "java", "com", "zeromail", "core", "inbox");

    /**
     * Forbidden bare-word identifiers. Matches the Java symbol via {@code \b} word boundaries, so
     * the matcher does NOT fire on these names appearing inside a string literal or as part of a
     * longer identifier (e.g. {@code subjectCiphertext} contains "subject" but is allowed because
     * it is bytes, not plaintext).
     */
    private static final List<String> FORBIDDEN_PLAINTEXT_IDENTIFIERS =
            List.of(
                    "subject",
                    "snippet",
                    "senderEmail",
                    "senderDisplayName",
                    "plaintext");

    /**
     * Captures the argument list of a log invocation. Group 1 is the method name; group 2 is the
     * raw argument text up to the closing parenthesis. The {@code [^)]*} cap is intentional — log
     * calls in this codebase do not nest balanced parens inside their argument list. If a future
     * call needs that shape, switch to a balanced-paren matcher.
     */
    private static final Pattern LOG_CALL =
            Pattern.compile("\\blog\\.(info|warn|error|debug|trace)\\s*\\(([^)]*)\\)");

    /**
     * Matches a JVM identifier outside of string literals. We strip double-quoted literals first
     * (see {@link #stripStringLiterals(String)}) so this regex never has to differentiate.
     */
    private static final Pattern BARE_IDENTIFIER = Pattern.compile("\\b([A-Za-z_][A-Za-z_0-9]*)\\b");

    @Test
    void no_inbox_module_log_call_passes_a_plaintext_field_identifier() throws IOException {
        Path moduleRoot = INBOX_MODULE_ROOT;
        // backend/core test working directory is backend/core; the module path is relative.
        assertThat(moduleRoot)
                .as(
                        "Source root must exist; this test runs from backend/core working dir."
                                + " Update INBOX_MODULE_ROOT if the layout changes.")
                .exists();

        List<String> violations = new ArrayList<>();
        try (Stream<Path> sourceFiles = Files.walk(moduleRoot)) {
            sourceFiles
                    .filter(file -> file.toString().endsWith(".java"))
                    .forEach(file -> scanFile(file, violations));
        }

        assertThat(violations)
                .as(
                        "core.inbox log calls must never reference plaintext field identifiers"
                                + " %s outside of string literals — the projection cipher exists"
                                + " precisely to keep these fields off the wire.",
                        FORBIDDEN_PLAINTEXT_IDENTIFIERS)
                .isEmpty();
    }

    private static void scanFile(Path file, List<String> violations) {
        String source;
        try {
            source = Files.readString(file);
        } catch (IOException readFailure) {
            throw new RuntimeException("Cannot read " + file, readFailure);
        }
        Matcher logCallMatcher = LOG_CALL.matcher(source);
        while (logCallMatcher.find()) {
            String argsWithoutStrings = stripStringLiterals(logCallMatcher.group(2));
            Matcher identifierMatcher = BARE_IDENTIFIER.matcher(argsWithoutStrings);
            while (identifierMatcher.find()) {
                String identifier = identifierMatcher.group(1);
                if (FORBIDDEN_PLAINTEXT_IDENTIFIERS.contains(identifier)) {
                    violations.add(
                            file.toString()
                                    + ": log."
                                    + logCallMatcher.group(1)
                                    + "(...) references forbidden plaintext identifier '"
                                    + identifier
                                    + "'");
                }
            }
        }
    }

    /**
     * Replace every double-quoted string literal with a placeholder so the identifier scan does
     * not match symbol-like substrings inside log message templates (e.g.
     * {@code "event=inbox_backfill_subject_failed"} contains "subject" only as text and is fine).
     * Handles backslash escapes ({@code \\}, {@code \"}) so an escaped quote does not terminate
     * the literal prematurely.
     */
    private static String stripStringLiterals(String input) {
        StringBuilder result = new StringBuilder(input.length());
        boolean insideString = false;
        for (int characterIndex = 0; characterIndex < input.length(); characterIndex++) {
            char currentChar = input.charAt(characterIndex);
            if (insideString) {
                if (currentChar == '\\' && characterIndex + 1 < input.length()) {
                    characterIndex++;
                    continue;
                }
                if (currentChar == '"') {
                    insideString = false;
                }
            } else {
                if (currentChar == '"') {
                    insideString = true;
                } else {
                    result.append(currentChar);
                }
            }
        }
        return result.toString();
    }
}
