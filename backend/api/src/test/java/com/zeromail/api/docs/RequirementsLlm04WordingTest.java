package com.zeromail.api.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class RequirementsLlm04WordingTest {

    @Test
    void llm04_wording_allows_encrypted_at_rest_byok_and_rejects_old_plaintext_phrase()
            throws IOException {
        String requirementsText = Files.readString(requirementsPath());

        assertThat(requirementsText).contains("encrypted-at-rest");
        assertThat(requirementsText).doesNotContain("no server-side persistence");
    }

    private static Path requirementsPath() {
        Path currentDirectory = Path.of("").toAbsolutePath();
        while (currentDirectory != null) {
            Path candidate = currentDirectory.resolve(".planning").resolve("REQUIREMENTS.md");
            if (Files.exists(candidate)) {
                return candidate;
            }
            currentDirectory = currentDirectory.getParent();
        }
        throw new IllegalStateException("Could not locate .planning/REQUIREMENTS.md");
    }
}
