package com.zeromail.core.admin.mkey.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmProviderMasterKeyLiquibaseContractTest {

    private static final Path CHANGELOG =
            Path.of("src/main/resources/db/changelog/changes/058-llm-provider-master-key.yaml");
    private static final Path MASTER_CHANGELOG =
            Path.of("src/main/resources/db/changelog/db.changelog-master.yaml");

    @Test
    void master_key_changelog_is_included_and_seeds_all_providers_unkeyed() throws IOException {
        assertThat(Files.exists(CHANGELOG)).isTrue();
        String changelog = Files.readString(CHANGELOG);
        String masterChangelog = Files.readString(MASTER_CHANGELOG);

        assertThat(masterChangelog).contains("changes/058-llm-provider-master-key.yaml");
        assertThat(changelog)
                .contains("llm_provider_master_key")
                .contains("provider_secret_version")
                .contains("feature_default_provider_chat")
                .contains("DEPRECATED")
                .contains("encrypted_key")
                .contains("nullable: true");
        for (String provider :
                List.of("OPENAI", "ANTHROPIC", "GOOGLE", "DEEPSEEK", "OPENROUTER", "ROUTER_9R")) {
            assertThat(changelog).contains(provider);
        }
        assertThat(changelog)
                .contains("feature_default_provider_chat: true")
                .contains("feature_default_provider_triage: true")
                .contains("feature_default_provider_draft: true");
    }
}
