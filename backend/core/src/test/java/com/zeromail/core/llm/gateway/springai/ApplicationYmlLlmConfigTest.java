package com.zeromail.core.llm.gateway.springai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ApplicationYmlLlmConfigTest {

    @Test
    void api_and_worker_yml_pin_platform_secret_and_observation_privacy() throws IOException {
        String apiApplicationYml = Files.readString(repoRoot()
                .resolve("backend/api/src/main/resources/application.yml"));
        String workerApplicationYml = Files.readString(repoRoot()
                .resolve("backend/worker/src/main/resources/application.yml"));

        assertThat(apiApplicationYml).contains("ZEROMAIL_LLM_PLATFORM_API_KEY:?");
        assertThat(workerApplicationYml).contains("ZEROMAIL_LLM_PLATFORM_API_KEY:?");
        assertThat(countMatches(apiApplicationYml, "log-prompt: false")).isGreaterThanOrEqualTo(2);
        assertThat(countMatches(apiApplicationYml, "log-completion: false")).isGreaterThanOrEqualTo(2);
        assertThat(countMatches(workerApplicationYml, "log-prompt: false")).isGreaterThanOrEqualTo(2);
        assertThat(countMatches(workerApplicationYml, "log-completion: false")).isGreaterThanOrEqualTo(2);
        assertThat(workerApplicationYml).contains("ZEROMAIL_LLM_DRIFT_ENABLED");
    }

    private static Path repoRoot() {
        Path currentDirectory = Path.of("").toAbsolutePath();
        while (currentDirectory != null
                && !Files.exists(currentDirectory.resolve("settings.gradle.kts"))) {
            currentDirectory = currentDirectory.getParent();
        }
        if (currentDirectory == null) {
            throw new IllegalStateException("Could not find repository root from test working directory");
        }
        return currentDirectory;
    }

    private static int countMatches(String content, String needle) {
        int count = 0;
        int searchIndex = 0;
        while ((searchIndex = content.indexOf(needle, searchIndex)) >= 0) {
            count++;
            searchIndex += needle.length();
        }
        return count;
    }
}
