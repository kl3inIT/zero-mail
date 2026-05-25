package com.zeromail.core.admin.cat.persistence.lowlevel;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.support.PostgresContainerTest;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Invariant: {@link ProviderCatalogLookupRepository#findAllFeatureDefaultPairs()} fetches every
 * {@code (provider, feature)} pair from {@code feature_default_provider} in a SINGLE round-trip.
 * The set returned uses {@code pairKey(provider, feature)} for membership testing so {@link
 * com.zeromail.core.admin.mkey.usecases.ProviderMasterKeyResolver#maskedRows()} can build the
 * per-row "is this the default for feature X?" flags without firing one extra query per (row,
 * feature) pair.
 *
 * <p>Test slice: {@link PostgresContainerTest} (real Postgres 18 + Liquibase schema). Per project
 * convention {@code @DataJpaTest} is not used here; see {@code OnboardingStepPersistenceTest} for
 * the rationale.
 */
class ProviderCatalogLookupRepositoryFindAllFeatureDefaultPairsIT extends PostgresContainerTest {

    @Autowired ProviderCatalogLookupRepository lookupRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanAndSeed() {
        // feature_default_provider.provider FK -> provider_catalog.provider — seed both
        // providers we will reference.
        jdbcTemplate.update(
                "DELETE FROM feature_tier_model WHERE feature IN ('CHAT','TRIAGE','DRAFT')");
        jdbcTemplate.update(
                "DELETE FROM feature_default_provider WHERE feature IN ('CHAT','TRIAGE','DRAFT')");
        insertProvider(
                LlmProvider.OPENROUTER,
                "OpenRouter",
                ProviderCatalogWriteRepository.KIND_COMPATIBLE_GATEWAY,
                "OPENAI_FORMAT",
                "https://openrouter.ai/api/v1");
        insertProvider(
                LlmProvider.OPENAI,
                "OpenAI",
                ProviderCatalogWriteRepository.KIND_SPRING_AI_BUILT_IN,
                "OPENAI_FORMAT",
                "https://api.openai.com/v1");

        // Seed exactly three pairs across the table — the assertion below uses the SAME three.
        // PK on feature_default_provider after changelog 081-04 is (feature, tier), so two rows
        // with the same feature but different tier may coexist; this test puts one row per
        // feature at PRIMARY to keep the seed minimal.
        insertPair("CHAT", LlmProvider.OPENROUTER);
        insertPair("TRIAGE", LlmProvider.OPENROUTER);
        insertPair("DRAFT", LlmProvider.OPENAI);
    }

    private void insertPair(String feature, LlmProvider provider) {
        jdbcTemplate.update(
                """
                INSERT INTO feature_default_provider (feature, tier, provider, updated_at)
                VALUES (?, 'PRIMARY', ?, now())
                """,
                feature,
                provider.id());
    }

    private void insertProvider(
            LlmProvider provider,
            String displayName,
            String providerKind,
            String compatibleType,
            String defaultBaseUrl) {
        jdbcTemplate.update(
                """
                INSERT INTO provider_catalog (
                    provider, display_name, provider_kind, compatible_type, default_base_url
                )
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (provider) DO UPDATE
                SET display_name = EXCLUDED.display_name,
                    provider_kind = EXCLUDED.provider_kind,
                    compatible_type = EXCLUDED.compatible_type,
                    default_base_url = EXCLUDED.default_base_url
                """,
                provider.id(),
                displayName,
                providerKind,
                compatibleType,
                defaultBaseUrl);
    }

    @Test
    @DisplayName(
            "findAllFeatureDefaultPairs returns every (provider, feature) pair encoded via"
                    + " pairKey")
    void returns_every_seeded_pair_via_pairkey_encoding() {
        Set<String> pairs = lookupRepository.findAllFeatureDefaultPairs();

        assertThat(pairs)
                .hasSize(3)
                .contains(
                        ProviderCatalogLookupRepository.pairKey(LlmProvider.OPENROUTER, "CHAT"),
                        ProviderCatalogLookupRepository.pairKey(LlmProvider.OPENROUTER, "TRIAGE"),
                        ProviderCatalogLookupRepository.pairKey(LlmProvider.OPENAI, "DRAFT"));
    }

    @Test
    @DisplayName(
            "findAllFeatureDefaultPairs is robust against empty result — returns an empty set, not"
                    + " null")
    void returns_empty_set_when_no_rows_present() {
        jdbcTemplate.update("DELETE FROM feature_tier_model");
        jdbcTemplate.update("DELETE FROM feature_default_provider");

        Set<String> pairs = lookupRepository.findAllFeatureDefaultPairs();

        assertThat(pairs).isEmpty();
    }
}
