package com.zeromail.core.admin.cat.persistence.lowlevel;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.ModelVerificationStatus;
import com.zeromail.core.admin.cat.projection.CatalogModelRow;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Raw JDBC read access for the curated catalog endpoint. ETag derivation, Redis caching, and JSON
 * serialisation stay in {@code CuratedCatalogQueryService}.
 */
@Repository
public class CuratedCatalogReadRepository {

    private final JdbcTemplate jdbcTemplate;

    public CuratedCatalogReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    public List<CatalogModelRowWithFeature> findEnabledCatalogRows() {
        // default_model is sourced from feature_tier_model position 1 of the PRIMARY tier — the
        // model the router tries first. feature_default_provider no longer carries model_id after
        // migration 082.
        return jdbcTemplate.query(
                """
                SELECT binding.feature,
                       model.provider,
                       model.model_id,
                       model.display_name,
                       (primary_default.model_id IS NOT NULL) AS default_model,
                       model.is_recommended,
                       model.cost_per_1k_input,
                       model.cost_per_1k_output,
                       model.verification_status,
                       model.deprecated_at
                FROM model_catalog model
                JOIN provider_catalog provider ON provider.provider = model.provider
                JOIN feature_binding binding ON binding.model_id = model.model_id
                LEFT JOIN feature_tier_model primary_default
                       ON primary_default.feature = binding.feature
                      AND primary_default.tier = 'PRIMARY'
                      AND primary_default.position = 1
                      AND primary_default.model_id = model.model_id
                WHERE provider.enabled = TRUE
                  AND binding.enabled = TRUE
                  AND model.deprecated_at IS NULL
                ORDER BY binding.feature, default_model DESC, model.is_recommended DESC, model.provider, model.model_id
                """,
                (resultSet, _) ->
                        new CatalogModelRowWithFeature(
                                Feature.fromId(resultSet.getString("feature")),
                                new CatalogModelRow(
                                        resultSet.getString("provider"),
                                        resultSet.getString("model_id"),
                                        resultSet.getString("display_name"),
                                        resultSet.getBoolean("default_model"),
                                        resultSet.getBoolean("is_recommended"),
                                        resultSet.getBigDecimal("cost_per_1k_input"),
                                        resultSet.getBigDecimal("cost_per_1k_output"),
                                        ModelVerificationStatus.fromId(
                                                resultSet.getString("verification_status")),
                                        null,
                                        0L)));
    }

    public List<String> findCatalogVersionFingerprintParts() {
        return jdbcTemplate.queryForList(
                """
                SELECT provider || ':' || catalog_version
                FROM provider_catalog
                ORDER BY provider
                """,
                String.class);
    }

    public record CatalogModelRowWithFeature(Feature feature, CatalogModelRow catalogModelRow) {}
}
