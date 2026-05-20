package com.zeromail.core.admin.cat.persistence.lowlevel;

import com.zeromail.core.admin.mkey.domain.LlmProvider;
import java.util.Objects;
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
}
