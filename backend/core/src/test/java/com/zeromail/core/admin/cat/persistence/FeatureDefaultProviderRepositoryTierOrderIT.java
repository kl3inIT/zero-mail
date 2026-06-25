package com.zeromail.core.admin.cat.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.RoutingTier;
import com.zeromail.core.admin.cat.persistence.lowlevel.ProviderCatalogWriteRepository;
import com.zeromail.core.llm.domain.LlmProvider;
import com.zeromail.core.support.PostgresContainerTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Invariant: {@link FeatureDefaultProviderRepository#findByFeatureOrderByTier(Feature)} returns the
 * feature's tier bindings in failover order (PRIMARY → FALLBACK → LAST_RESORT) regardless of the
 * order rows were written.
 *
 * <p>Regression: {@code tier} is {@code @Enumerated(STRING)}, so the original {@code order by tier}
 * JPQL sorted the enum <em>names</em> lexically — {@code FALLBACK < LAST_RESORT < PRIMARY} — which
 * inverted the failover chain. In production this made every feature try its OPENAI FALLBACK before
 * its ROUTER_9R PRIMARY once a FALLBACK tier was configured. {@link
 * com.zeromail.core.admin.cat.usecases.LlmRouterWalkOrderTest} mocks this repository and so cannot
 * catch the DB-side ordering; this IT exercises the real query + in-app weight sort.
 *
 * <p>Test slice: {@link PostgresContainerTest} (real Postgres 18 + Liquibase schema), matching the
 * sibling {@code ProviderCatalogLookupRepositoryFindAllFeatureDefaultPairsIT}.
 */
class FeatureDefaultProviderRepositoryTierOrderIT extends PostgresContainerTest {

    private static final Feature FEATURE = Feature.TRIAGE_SEMANTIC;

    @Autowired FeatureDefaultProviderRepository repository;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanAndSeed() {
        jdbcTemplate.update("DELETE FROM feature_tier_model WHERE feature = ?", FEATURE.name());
        jdbcTemplate.update(
                "DELETE FROM feature_default_provider WHERE feature = ?", FEATURE.name());
        insertProvider(LlmProvider.OPENROUTER, "OpenRouter", "https://openrouter.ai/api/v1");
        insertProvider(LlmProvider.OPENAI, "OpenAI", "https://api.openai.com/v1");

        // Insert in scrambled tier order on purpose so a lexical/string sort would surface a wrong
        // order and only a weight-based sort yields PRIMARY first.
        insertTier(RoutingTier.FALLBACK, LlmProvider.OPENAI);
        insertTier(RoutingTier.LAST_RESORT, LlmProvider.OPENAI);
        insertTier(RoutingTier.PRIMARY, LlmProvider.OPENROUTER);
    }

    @Test
    @DisplayName("findByFeatureOrderByTier returns PRIMARY → FALLBACK → LAST_RESORT, not lexical")
    void orders_by_tier_weight_not_enum_name() {
        List<FeatureDefaultProviderEntity> bindings = repository.findByFeatureOrderByTier(FEATURE);

        assertThat(bindings)
                .extracting(FeatureDefaultProviderEntity::getTier)
                .containsExactly(
                        RoutingTier.PRIMARY, RoutingTier.FALLBACK, RoutingTier.LAST_RESORT);
    }

    @Test
    @DisplayName("findAllOrderedByFeatureAndTier keeps PRIMARY above FALLBACK within a feature")
    void matrix_keeps_primary_above_fallback() {
        List<FeatureDefaultProviderEntity> matrix =
                repository.findAllOrderedByFeatureAndTier().stream()
                        .filter(binding -> binding.getFeature() == FEATURE)
                        .toList();

        assertThat(matrix)
                .extracting(FeatureDefaultProviderEntity::getTier)
                .containsExactly(
                        RoutingTier.PRIMARY, RoutingTier.FALLBACK, RoutingTier.LAST_RESORT);
    }

    private void insertTier(RoutingTier tier, LlmProvider provider) {
        jdbcTemplate.update(
                """
                INSERT INTO feature_default_provider (feature, tier, provider, updated_at)
                VALUES (?, ?, ?, now())
                """,
                FEATURE.name(),
                tier.id(),
                provider.id());
    }

    private void insertProvider(LlmProvider provider, String displayName, String defaultBaseUrl) {
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
                ProviderCatalogWriteRepository.KIND_COMPATIBLE_GATEWAY,
                "OPENAI_FORMAT",
                defaultBaseUrl);
    }
}
