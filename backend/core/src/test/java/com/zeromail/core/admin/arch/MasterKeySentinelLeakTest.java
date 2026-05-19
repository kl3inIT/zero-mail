package com.zeromail.core.admin.arch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("ci-gate")
class MasterKeySentinelLeakTest {

    private static final List<Pattern> RAW_SECRET_PATTERNS =
            List.of(
                    Pattern.compile("sk-[A-Za-z0-9]{16,}"),
                    Pattern.compile("sk-ant-[A-Za-z0-9]{16,}"),
                    Pattern.compile("sk-or-[A-Za-z0-9]{16,}"),
                    Pattern.compile("AIza[A-Za-z0-9_\\-]{16,}"));
    private static final Pattern BASE64_TOKEN = Pattern.compile("[A-Za-z0-9+/]{24,}={0,2}");
    private static final Pattern HEX_TOKEN = Pattern.compile("[A-Fa-f0-9]{32,}");

    @Test
    void scanner_detects_raw_base64_and_hex_encoded_master_key_shapes(@TempDir Path tempDir)
            throws IOException {
        String rawSecret = "sk-" + "projabcdefghij1234567890";
        String base64Secret =
                Base64.getEncoder().encodeToString(rawSecret.getBytes(StandardCharsets.UTF_8));
        String hexSecret = HexFormat.of().formatHex(rawSecret.getBytes(StandardCharsets.UTF_8));
        Path fixture = tempDir.resolve("fixture.log");
        Files.writeString(
                fixture,
                "raw="
                        + rawSecret
                        + System.lineSeparator()
                        + "base64="
                        + base64Secret
                        + System.lineSeparator()
                        + "hex="
                        + hexSecret,
                StandardCharsets.UTF_8);

        assertThat(scanDirectory(tempDir)).hasSize(3);
    }

    @Test
    void scanner_allows_masked_key_shapes(@TempDir Path tempDir) throws IOException {
        Path fixture = tempDir.resolve("masked.json");
        Files.writeString(
                fixture,
                "{\"masked_key\":\"sk-****abc1\",\"anthropic\":\"sk-ant-****abc1\"}",
                StandardCharsets.UTF_8);

        assertThat(scanDirectory(tempDir)).isEmpty();
    }

    @Test
    void build_outputs_do_not_contain_raw_master_key_shapes() throws IOException {
        List<Path> scanRoots =
                Stream.of(
                                Path.of("build/reports/tests"),
                                Path.of("build/test-results"),
                                Path.of("build/logs"))
                        .filter(Files::exists)
                        .toList();

        List<String> findings = new ArrayList<>();
        for (Path scanRoot : scanRoots) {
            findings.addAll(scanDirectory(scanRoot));
        }

        assertThat(findings).isEmpty();
    }

    private static List<String> scanDirectory(Path root) throws IOException {
        List<String> findings = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path candidate : paths.filter(Files::isRegularFile).toList()) {
                if (!isScannable(candidate)) {
                    continue;
                }
                String content = Files.readString(candidate, StandardCharsets.UTF_8);
                findings.addAll(scanContent(candidate, content));
            }
        }
        return findings;
    }

    private static boolean isScannable(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".log")
                || fileName.endsWith(".out")
                || fileName.endsWith(".json")
                || fileName.endsWith(".xml")
                || fileName.endsWith(".txt");
    }

    private static List<String> scanContent(Path path, String content) {
        List<String> findings = new ArrayList<>();
        if (containsRawSecret(content)) {
            findings.add(path + ":raw");
        }
        Matcher base64Matcher = BASE64_TOKEN.matcher(content);
        while (base64Matcher.find()) {
            if (containsRawSecret(decodeBase64(base64Matcher.group()))) {
                findings.add(path + ":base64");
            }
        }
        Matcher hexMatcher = HEX_TOKEN.matcher(content);
        while (hexMatcher.find()) {
            if (containsRawSecret(decodeHex(hexMatcher.group()))) {
                findings.add(path + ":hex");
            }
        }
        return findings;
    }

    private static boolean containsRawSecret(String content) {
        return RAW_SECRET_PATTERNS.stream()
                .anyMatch(rawSecretPattern -> rawSecretPattern.matcher(content).find());
    }

    private static String decodeBase64(String token) {
        try {
            return new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException invalidBase64) {
            return "";
        }
    }

    private static String decodeHex(String token) {
        try {
            return new String(HexFormat.of().parseHex(token), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException invalidHex) {
            return "";
        }
    }
}
