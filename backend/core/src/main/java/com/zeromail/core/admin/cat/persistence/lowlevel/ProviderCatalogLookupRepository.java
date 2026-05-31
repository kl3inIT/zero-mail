package com.zeromail.core.admin.cat.persistence.lowlevel;

import com.zeromail.core.llm.domain.LlmProvider;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Small lookup helpers over {@code provider_catalog} and {@code feature_default_provider} for the
 * LLM gateway resolver. Falls open on data-access exceptions (returns the conservative default)
 * because callers cache aggressively — a transient DB blip must not fail-open into the wrong
 * provider config.
 */
@Repository
public class ProviderCatalogLookupRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProviderCatalogLookupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    public long findCatalogVersionOrOne(LlmProvider provider) {
        try {
            Long catalogVersion =
                    jdbcTemplate.queryForObject(
                            "SELECT catalog_version FROM provider_catalog WHERE provider = ?",
                            Long.class,
                            provider.id());
            return catalogVersion == null ? 1L : catalogVersion;
        } catch (DataAccessException dataAccessException) {
            return 1L;
        }
    }

    public boolean isFeatureDefaultProvider(LlmProvider provider, String feature) {
        try {
            Integer count =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM feature_default_provider
                            WHERE provider = ?
                              AND feature = ?
                            """,
                            Integer.class,
                            provider.id(),
                            feature);
            return count != null && count > 0;
        } catch (DataAccessException dataAccessException) {
            return false;
        }
    }

    /**
     * Single-shot fetch of every (provider, feature) pair currently configured in {@code
     * feature_default_provider}. Use this when rendering a multi-row view that would otherwise call
     * {@link #isFeatureDefaultProvider} once per (row, feature) — the round-trip cost over a
     * SSH-tunnelled connection adds up fast.
     */
    public Set<String> findAllFeatureDefaultPairs() {
        try {
            Set<String> pairs = new HashSet<>();
            jdbcTemplate.query(
                    "SELECT provider, feature FROM feature_default_provider",
                    resultSet -> {
                        pairs.add(
                                resultSet.getString("provider")
                                        + "|"
                                        + resultSet.getString("feature"));
                    });
            return pairs;
        } catch (DataAccessException dataAccessException) {
            return Set.of();
        }
    }

    public Map<String, ProviderMetadata> findAllProviderMetadata() {
        try {
            Map<String, ProviderMetadata> providers = new LinkedHashMap<>();
            jdbcTemplate.query(
                    """
                    SELECT provider,
                           COALESCE(display_name, provider) AS display_name,
                           COALESCE(provider_kind, 'COMPATIBLE_GATEWAY') AS provider_kind,
                           compatible_type,
                           default_base_url
                    FROM provider_catalog
                    ORDER BY provider
                    """,
                    resultSet -> {
                        String providerId = resultSet.getString("provider");
                        providers.put(
                                providerId,
                                new ProviderMetadata(
                                        providerId,
                                        resultSet.getString("display_name"),
                                        resultSet.getString("provider_kind"),
                                        resultSet.getString("compatible_type"),
                                        resultSet.getString("default_base_url")));
                    });
            return providers;
        } catch (DataAccessException dataAccessException) {
            return Map.of();
        }
    }

    public ProviderMetadata findProviderMetadataOrNull(LlmProvider provider) {
        try {
            return jdbcTemplate
                    .query(
                            """
              SELECT provider,
                     COALESCE(display_name, provider) AS display_name,
                     COALESCE(provider_kind, 'COMPATIBLE_GATEWAY') AS provider_kind,
                     compatible_type,
                     default_base_url
              FROM provider_catalog
              WHERE provider = ?
              """,
                            (resultSet, _) ->
                                    new ProviderMetadata(
                                            resultSet.getString("provider"),
                                            resultSet.getString("display_name"),
                                            resultSet.getString("provider_kind"),
                                            resultSet.getString("compatible_type"),
                                            resultSet.getString("default_base_url")),
                            provider.id())
                    .stream()
                    .findFirst()
                    .orElse(null);
        } catch (DataAccessException dataAccessException) {
            return null;
        }
    }

    public static String pairKey(LlmProvider provider, String feature) {
        return provider.id() + "|" + feature;
    }

    public record ProviderMetadata(
            String providerId,
            String displayName,
            String providerKind,
            String compatibleType,
            String defaultBaseUrl) {}
}
